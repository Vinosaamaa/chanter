package com.chanter.common.lifecycle;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded page staging. READY is committed only with the verified final count and digest. */
public final class DeletedScopeStore {
    public static final String SCHEMA="""
        CREATE TABLE lifecycle_scope_imports (
            study_server_id UUID NOT NULL, scope_kind VARCHAR(8) NOT NULL CHECK(scope_kind IN ('COURSE','CHANNEL')),
            revision BIGINT NOT NULL, event_id UUID NOT NULL, terminal_digest VARCHAR(64) NOT NULL,
            total_count BIGINT NOT NULL CHECK(total_count>=0), scope_digest VARCHAR(64) NOT NULL,
            received_count BIGINT NOT NULL, after_id UUID NOT NULL, rolling_digest VARCHAR(64) NOT NULL, ready BOOLEAN NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_scope_import_ids (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,scope_id UUID NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,scope_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_scope_imports(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_scope_import_pages (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,after_id UUID NOT NULL,page_digest VARCHAR(64) NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,after_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_scope_imports(study_server_id,scope_kind)
        )
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Consumer<TerminalJournal.Entry> authority;
    private final Consumer<TerminalJournal.Entry> reconcile;
    public DeletedScopeStore(JdbcTemplate jdbc,TransactionTemplate tx,Consumer<TerminalJournal.Entry> authority,
            Consumer<TerminalJournal.Entry> reconcile) {
        this.jdbc=jdbc; this.tx=tx; this.authority=authority; this.reconcile=reconcile;
    }

    public DeletedScope.Receipt accept(DeletedScope.Import request) {
        request.validate();
        return tx.execute(status -> {
            // Same ordering as terminal replay; no page may race target allocation or reconciliation.
            jdbc.queryForObject("SELECT revision FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Long.class);
            authority.accept(request.entry());
            var page=request.page();
            var rows=headers(page.studyServerId(),page.kind());
            Header header;
            if(rows.isEmpty()) {
                if(!DeletedScope.START.equals(page.after())) throw new IllegalArgumentException("Missing scope prefix");
                header=new Header(page.terminalRevision(),page.terminalEventId(),page.terminalDigest(),page.totalCount(),page.scopeDigest(),
                        0,DeletedScope.START,DeletedScope.startDigest(page.terminalDigest(),page.kind(),page.totalCount()),false);
                jdbc.update("INSERT INTO lifecycle_scope_imports VALUES (?,?,?,?,?,?,?,?,?,?,?)",page.studyServerId(),page.kind(),
                        header.revision(),header.event(),header.terminalDigest(),header.total(),header.digest(),0,header.after(),header.rolling(),false);
            } else header=rows.getFirst();
            match(header,page);
            var accepted=jdbc.query("SELECT page_digest FROM lifecycle_scope_import_pages WHERE study_server_id=? AND scope_kind=? AND after_id=?",
                    (rs,row)->rs.getString(1),page.studyServerId(),page.kind(),page.after());
            if(!accepted.isEmpty()) {
                if(!accepted.getFirst().equals(page.pageDigest())) throw new IllegalArgumentException("Scope replay changed");
                if(header.ready()) reconcile.accept(request.entry());
                return receipt(page,header);
            }
            if(header.ready() || !header.after().equals(page.after())) throw new IllegalArgumentException("Scope cursor changed");
            long count=Math.addExact(header.received(),page.ids().size());
            String rolling=header.rolling();
            for(UUID id:page.ids()) rolling=DeletedScope.nextDigest(rolling,id);
            boolean ready=page.nextAfter()==null;
            if(count>header.total() || ready && (count!=header.total() || !rolling.equals(header.digest()))
                    || !ready && count>=header.total()) throw new IllegalArgumentException("Incomplete scope digest/count");
            for(UUID id:page.ids()) jdbc.update("INSERT INTO lifecycle_scope_import_ids VALUES (?,?,?)",page.studyServerId(),page.kind(),id);
            UUID after=page.ids().isEmpty() ? page.after() : page.ids().getLast();
            jdbc.update("UPDATE lifecycle_scope_imports SET received_count=?,after_id=?,rolling_digest=?,ready=? WHERE study_server_id=? AND scope_kind=?",
                    count,after,rolling,ready,page.studyServerId(),page.kind());
            jdbc.update("INSERT INTO lifecycle_scope_import_pages VALUES (?,?,?,?)",page.studyServerId(),page.kind(),page.after(),page.pageDigest());
            if(ready) reconcile.accept(request.entry());
            return receipt(page,new Header(header.revision(),header.event(),header.terminalDigest(),header.total(),header.digest(),count,after,rolling,ready));
        });
    }

    public boolean ready(TerminalJournal.Entry entry,String kind) {
        entry.validate(); DeletedScope.requireKind(kind);
        var rows=headers(entry.targetId(),kind);
        return rows.size()==1 && rows.getFirst().ready() && rows.getFirst().revision()==entry.revision()
                && rows.getFirst().event().equals(entry.eventId()) && rows.getFirst().terminalDigest().equals(entry.digest());
    }

    public DeletedScope.Page page(UUID server,long revision,UUID event,String digest,String kind,UUID after,int limit) {
        if(after==null || limit<1 || limit>DeletedScope.MAX_PAGE) throw new IllegalArgumentException("Invalid scope page");
        DeletedScope.requireKind(kind);
        var rows=headers(server,kind);
        if(rows.size()!=1 || !rows.getFirst().ready()) throw new IllegalArgumentException("Scope is not READY");
        var header=rows.getFirst();
        if(header.revision()!=revision || !header.event().equals(event) || !header.terminalDigest().equals(digest))
            throw new IllegalArgumentException("Scope authority changed");
        var ids=jdbc.query("SELECT scope_id FROM lifecycle_scope_import_ids WHERE study_server_id=? AND scope_kind=? AND scope_id>? ORDER BY scope_id LIMIT ?",
                (rs,row)->rs.getObject(1,UUID.class),server,kind,after,limit+1);
        boolean more=ids.size()>limit; ids=List.copyOf(ids.subList(0,Math.min(limit,ids.size())));
        var page=new DeletedScope.Page(1,server,revision,event,digest,kind,after,header.total(),header.digest(),ids,more ? ids.getLast() : null);
        page.validate(); return page;
    }
    private List<Header> headers(UUID server,String kind) {
        return jdbc.query("SELECT revision,event_id,terminal_digest,total_count,scope_digest,received_count,after_id,rolling_digest,ready FROM lifecycle_scope_imports WHERE study_server_id=? AND scope_kind=?",
                (rs,row)->new Header(rs.getLong(1),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getLong(6),
                        rs.getObject(7,UUID.class),rs.getString(8),rs.getBoolean(9)),server,kind);
    }
    private static void match(Header header,DeletedScope.Page page) {
        if(header.revision()!=page.terminalRevision() || !header.event().equals(page.terminalEventId()) || !header.terminalDigest().equals(page.terminalDigest())
                || header.total()!=page.totalCount() || !header.digest().equals(page.scopeDigest())) throw new IllegalArgumentException("Scope authority changed");
    }
    private static DeletedScope.Receipt receipt(DeletedScope.Page page,Header header) {
        return new DeletedScope.Receipt(1,page.studyServerId(),header.revision(),header.event(),header.terminalDigest(),page.kind(),header.total(),
                header.digest(),header.received(),header.after(),header.ready());
    }
    private record Header(long revision,UUID event,String terminalDigest,long total,String digest,long received,UUID after,String rolling,boolean ready) { }
}
