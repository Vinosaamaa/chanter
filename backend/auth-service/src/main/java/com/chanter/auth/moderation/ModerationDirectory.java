package com.chanter.auth.moderation;

import com.chanter.common.auth.ModerationDirectoryItem;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationDirectory {
    private final JdbcTemplate jdbc;
    private final OperatorAccess operators;
    private final ModerationAudit audit;
    private final ReportEvidenceClient sources;
    public ModerationDirectory(JdbcTemplate jdbc,OperatorAccess operators,ModerationAudit audit,ReportEvidenceClient sources) {
        this.jdbc=jdbc; this.operators=operators; this.audit=audit; this.sources=sources;
    }

    @Transactional
    public List<ModerationDirectoryItem> search(String authorization,String verification,String type,String query,int offset,String reason,UUID correlation) {
        var operator=operators.requireStepUp(authorization,verification); operator.requireAdmin();
        OperatorRoles.requireReason(reason);
        if(!List.of("USER","STUDY_SERVER").contains(type) || query==null || query.strip().length()<3 || query.length()>120 || offset<0 || offset>10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a search of 3 to 120 characters and a valid page");
        var result=type.equals("USER") ? jdbc.query("""
                SELECT id,display_name FROM auth_users WHERE LOWER(display_name) LIKE ? ESCAPE '!'
                  OR LOWER(email)=? OR CAST(id AS VARCHAR)=? ORDER BY display_name,id LIMIT 50 OFFSET ?
                """,(rs,row)->new ModerationDirectoryItem("USER",rs.getObject(1,UUID.class),rs.getString(2)),
                "%"+query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%",
                query.strip().toLowerCase(Locale.ROOT),query.strip().toLowerCase(Locale.ROOT),offset) : sources.searchStudyServers(query,offset);
        audit.append(operator.userId(),"MODERATION_DIRECTORY_READ",type,reason,correlation,"",Integer.toString(result.size()));
        return result;
    }
}
