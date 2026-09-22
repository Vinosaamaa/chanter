package com.chanter.common.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Content-free, original-authority-bound Course/channel scope retained across physical recovery points. */
public final class DeletedScope {
    public static final UUID START=new UUID(0,0);
    public static final int MAX_PAGE=256;
    private DeletedScope() { }

    public static String startDigest(String terminalDigest,String kind,long count) {
        requireDigest(terminalDigest); requireKind(kind);
        if(count<0) throw new IllegalArgumentException("Invalid scope count");
        return hash("deleted-study-server-scope\n1\n"+terminalDigest+"\n"+kind+"\n"+count+"\n");
    }
    public static String nextDigest(String previous,UUID id) {
        requireDigest(previous);
        if(id==null || START.equals(id)) throw new IllegalArgumentException("Invalid scope ID");
        return hash(previous+"\n"+id+"\n");
    }
    public static void requireKind(String kind) {
        if(!"COURSE".equals(kind) && !"CHANNEL".equals(kind)) throw new IllegalArgumentException("Invalid scope kind");
    }
    private static void requireDigest(String digest) {
        if(digest==null || !digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid scope digest");
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Page(int schemaVersion,UUID studyServerId,long terminalRevision,UUID terminalEventId,String terminalDigest,
            String kind,UUID after,long totalCount,String scopeDigest,List<UUID> ids,UUID nextAfter) {
        public Page { ids=List.copyOf(ids); }
        public void validate() {
            TerminalJournal.requireTarget("STUDY_SERVER",studyServerId); requireKind(kind);
            requireDigest(terminalDigest); requireDigest(scopeDigest);
            if(schemaVersion!=1 || terminalRevision<1 || terminalEventId==null || START.equals(terminalEventId)
                    || after==null || totalCount<0 || ids.size()>MAX_PAGE || ids.size()>totalCount)
                throw new IllegalArgumentException("Invalid scope page");
            UUID previous=after;
            for(UUID id:ids) {
                // PostgreSQL UUID ordering is unsigned byte order, equal to canonical lowercase text order.
                if(id==null || id.toString().compareTo(previous.toString())<=0) throw new IllegalArgumentException("Unordered scope IDs");
                previous=id;
            }
            if(nextAfter!=null && (ids.isEmpty() || !nextAfter.equals(previous))
                    || ids.isEmpty() && (totalCount!=0 || !START.equals(after) || nextAfter!=null))
                throw new IllegalArgumentException("Invalid scope cursor");
        }
        public void requireEntry(TerminalJournal.Entry entry) {
            validate(); entry.validate();
            if(!"STUDY_SERVER".equals(entry.targetKind()) || !studyServerId.equals(entry.targetId())
                    || terminalRevision!=entry.revision() || !terminalEventId.equals(entry.eventId()) || !terminalDigest.equals(entry.digest()))
                throw new IllegalArgumentException("Scope authority changed");
        }
        public String pageDigest() {
            validate();
            StringBuilder text=new StringBuilder("deleted-study-server-scope-page\n1\n").append(terminalDigest).append('\n')
                    .append(kind).append('\n').append(after).append('\n').append(totalCount).append('\n').append(scopeDigest).append('\n');
            ids.forEach(id -> text.append(id).append('\n'));
            return hash(text.append(nextAfter==null ? "END" : nextAfter).append('\n').toString());
        }
    }
    public record Import(TerminalJournal.Entry entry,Page page) {
        public void validate() {
            if(entry==null || page==null) throw new IllegalArgumentException("Missing scope authority");
            page.requireEntry(entry);
        }
    }
    public record Receipt(int schemaVersion,UUID studyServerId,long terminalRevision,UUID terminalEventId,String terminalDigest,
            String kind,long totalCount,String scopeDigest,long receivedCount,UUID after,boolean ready) { }
}
