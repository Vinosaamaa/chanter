package com.chanter.auth.moderation;

import com.chanter.common.auth.ReportEvidence;
import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.application.EmailSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationCases {
    private final JdbcTemplate jdbc;
    private final ReportEvidenceClient sources;
    private final OperatorAccess operators;
    private final ModerationAudit audit;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final ModerationRestrictions restrictions;
    private final AuthUserRepository users;
    private final EmailSender email;
    private final String publicBaseUrl;

    public ModerationCases(JdbcTemplate jdbc,ReportEvidenceClient sources,OperatorAccess operators,
            ModerationAudit audit,ObjectMapper mapper,PlatformTransactionManager transactions,
            ModerationRestrictions restrictions,AuthUserRepository users,EmailSender email,
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.jdbc=jdbc; this.sources=sources; this.operators=operators; this.audit=audit; this.mapper=mapper;
        this.tx=new TransactionTemplate(transactions);
        this.restrictions=restrictions; this.users=users; this.email=email;
        this.publicBaseUrl=publicBaseUrl.replaceAll("/+$", "");
    }

    public Report submit(UUID reporter,String type,UUID source,String reason) {
        OperatorRoles.requireReason(reason);
        ModerationRestrictions.requireType(type);
        // Source-owned authorization completes before opening the local write transaction.
        ReportEvidence evidence=sources.read(reporter,type,source);
        String snapshot=encode(evidence);
        if(snapshot.length()>24000) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Evidence snapshot is too large");
        return tx.execute(status -> {
            UUID id=UUID.randomUUID();
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
            jdbc.update("""
                    INSERT INTO moderation_reports(id,reporter_id,target_type,target_id,reason,evidence,status,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,'NEW',?,?)
                    """,id,reporter,type,source,reason.strip(),snapshot,now,now);
            return new Report(id,type,source,reason.strip(),"NEW",null,now.toInstant());
        });
    }

    public Report ownReport(UUID reporter,UUID id) {
        return jdbc.query("SELECT * FROM moderation_reports WHERE id=? AND reporter_id=?",(rs,row)->report(rs),id,reporter)
                .stream().findFirst().orElseThrow(ModerationCases::missing);
    }

    public List<Report> ownReports(UUID reporter) {
        return jdbc.query("SELECT * FROM moderation_reports WHERE reporter_id=? ORDER BY created_at DESC,id LIMIT 50",
                (rs,row)->report(rs),reporter);
    }

    @Transactional
    public Detail detail(String authorization,String verification,UUID id,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        OperatorRoles.requireReason(reason);
        var row=accessible(id,operator);
        audit.append(operator.userId(),"EVIDENCE_READ",id.toString(),reason,correlation,"","");
        var notes=jdbc.query("SELECT id,actor_id,body,created_at FROM moderation_notes WHERE report_id=? ORDER BY created_at,id LIMIT 100",
                (rs,index)->new Note(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                        rs.getObject(4,OffsetDateTime.class).toInstant()),id);
        return new Detail(row.report(),row.reporter(),row.assignedTo(),decode(row.snapshot()),notes,restrictions.list(id));
    }

    @Transactional
    public List<QueueItem> queue(String authorization,String verification,String status,String reason,UUID correlation) {
        return queue(authorization,verification,status,"",0,null,reason,correlation);
    }

    @Transactional
    public List<QueueItem> queue(String authorization,String verification,String status,String query,int offset,UUID target,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        OperatorRoles.requireReason(reason);
        if(query==null || query.length()>120 || offset<0 || offset>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid report search or page");
        if(status!=null && !List.of("NEW","ASSIGNED","RESOLVED","ESCALATED").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported report status");
        audit.append(operator.userId(),"REPORT_QUEUE_READ","reports",reason,correlation,"",status==null ? "ALL" : status);
        return jdbc.query("""
                SELECT * FROM moderation_reports WHERE (?=TRUE OR assigned_to=?) AND (CAST(? AS VARCHAR) IS NULL OR status=?)
                  AND (LOWER(reason) LIKE ? ESCAPE '!' OR CAST(id AS VARCHAR)=?)
                  AND (CAST(? AS UUID) IS NULL OR target_id=?)
                ORDER BY created_at DESC,id LIMIT 50 OFFSET ?
                """,(rs,row)->new QueueItem(report(rs),rs.getObject("assigned_to",UUID.class)),
                operator.role()==OperatorAccess.Role.ADMIN,operator.userId(),status,status,
                "%"+query.strip().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%",query.strip(),target,target,offset);
    }

    @Transactional
    public void assign(String authorization,String verification,UUID id,UUID assignee,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification); operator.requireAdmin();
        OperatorRoles.requireReason(reason);
        var row=accessible(id,operator);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_operators WHERE user_id=? AND revoked_at IS NULL)",Boolean.class,assignee)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an active platform operator");
        jdbc.update("UPDATE moderation_reports SET assigned_to=?,status='ASSIGNED',updated_at=? WHERE id=?",assignee,OffsetDateTime.now(ZoneOffset.UTC),id);
        audit.append(operator.userId(),"REPORT_ASSIGNED",id.toString(),reason,correlation,String.valueOf(row.assignedTo()),assignee.toString());
    }

    @Transactional
    public void note(String authorization,String verification,UUID id,String body,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        OperatorRoles.requireReason(reason); accessible(id,operator);
        if(body==null || body.isBlank() || body.length()>4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A note of at most 4000 characters is required");
        UUID note=UUID.randomUUID();
        jdbc.update("INSERT INTO moderation_notes(id,report_id,actor_id,body,created_at) VALUES(?,?,?,?,?)",
                note,id,operator.userId(),body.strip(),OffsetDateTime.now(ZoneOffset.UTC));
        audit.append(operator.userId(),"REPORT_NOTE_ADDED",id.toString(),reason,correlation,"",note.toString());
    }

    @Transactional
    public void resolve(String authorization,String verification,UUID id,String status,String resolution,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        OperatorRoles.requireReason(reason); OperatorRoles.requireReason(resolution);
        if(!List.of("RESOLVED","ESCALATED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose resolve or escalate");
        var row=accessible(id,operator);
        jdbc.update("UPDATE moderation_reports SET status=?,resolution=?,updated_at=? WHERE id=?",status,resolution.strip(),OffsetDateTime.now(ZoneOffset.UTC),id);
        audit.append(operator.userId(),"REPORT_STATUS_CHANGED",id.toString(),reason,correlation,row.report().status(),status);
    }

    private Row accessible(UUID id,OperatorAccess.Operator operator) {
        var row=jdbc.query("SELECT * FROM moderation_reports WHERE id=? FOR UPDATE",(rs,index)->new Row(report(rs),
                rs.getObject("reporter_id",UUID.class),rs.getObject("assigned_to",UUID.class),rs.getString("evidence")),id)
                .stream().findFirst().orElseThrow(ModerationCases::missing);
        if(operator.role()!=OperatorAccess.Role.ADMIN && !operator.userId().equals(row.assignedTo())) throw missing();
        return row;
    }

    @Transactional
    public void restrict(String authorization,String verification,UUID report,UUID operation,String type,UUID target,
            String reason,Instant expires,String confirmation,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        var row=accessible(report,operator);
        if(row.report().status().equals("RESOLVED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolved reports cannot receive new restrictions");
        var evidence=decode(row.snapshot());
        requireActionScope(operator,evidence,type,target,confirmation);
        restrictions.add(operation,report,type,target,operator.userId(),reason,expires,correlation);
        notifyRestriction(type.equals("USER") ? target : evidence.authorId(), operation,
                "Chanter moderation restriction", "A " + type.toLowerCase(java.util.Locale.ROOT)
                        + " restriction was applied until " + expires + ".\nReason: " + reason);
    }

    @Transactional
    public void reinstate(String authorization,String verification,UUID report,UUID restriction,String reason,
            String confirmation,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification);
        var row=accessible(report,operator);
        var evidence=decode(row.snapshot());
        var record=restrictions.get(report,restriction);
        requireActionScope(operator,evidence,record.type(),record.targetId(),confirmation);
        if(restrictions.revoke(report,restriction,operator.userId(),reason,correlation)) {
            notifyRestriction(record.type().equals("USER") ? record.targetId() : evidence.authorId(), restriction,
                    "Chanter moderation review", "Restriction " + restriction + " was lifted.\nReason: " + reason
                            + "\nOther active restrictions, if any, still apply.");
        }
    }

    private static void requireActionScope(OperatorAccess.Operator operator,ReportEvidence evidence,
            String type,UUID target,String confirmation) {
        ModerationRestrictions.requireType(type);
        if(target==null || !target.toString().equals(confirmation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirm the exact target ID");
        boolean exactSource=type.equals(evidence.type()) && target.equals(evidence.id());
        boolean author=type.equals("USER") && target.equals(evidence.authorId());
        if(!exactSource && !author)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Action must target this case's preserved source or author");
        if(type.equals("USER") || type.equals("STUDY_SERVER")) operator.requireAdmin();
    }

    private void notifyRestriction(UUID recipient,UUID restriction,String subject,String message) {
        if(recipient==null) return;
        users.findById(recipient).filter(user -> user.emailVerified()).ifPresent(user ->
                email.send(user.email(),subject,message+"\n\nRestriction reference: "+restriction
                        +"\nRequest an appeal link: "+publicBaseUrl+"/appeal?restriction="+restriction));
    }

    private String encode(ReportEvidence evidence) {
        try{return mapper.writeValueAsString(evidence);}catch(JsonProcessingException invalid){throw new IllegalStateException("Could not preserve report evidence",invalid);}
    }
    private ReportEvidence decode(String evidence) {
        try{return mapper.readValue(evidence,ReportEvidence.class);}catch(JsonProcessingException invalid){throw new IllegalStateException("Could not read preserved evidence",invalid);}
    }
    private static Report report(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Report(rs.getObject("id",UUID.class),rs.getString("target_type"),rs.getObject("target_id",UUID.class),
                rs.getString("reason"),rs.getString("status"),rs.getString("resolution"),rs.getObject("created_at",OffsetDateTime.class).toInstant());
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Report not found");}
    private record Row(Report report,UUID reporter,UUID assignedTo,String snapshot){}
    public record Report(UUID id,String targetType,UUID targetId,String reason,String status,String resolution,Instant createdAt){}
    public record Note(UUID id,UUID actorId,String body,Instant createdAt){}
    public record Detail(Report report,UUID reporterId,UUID assignedTo,ReportEvidence evidence,List<Note> notes,
            List<ModerationRestrictions.Restriction> restrictions){}
    public record QueueItem(Report report,UUID assignedTo){}
}
