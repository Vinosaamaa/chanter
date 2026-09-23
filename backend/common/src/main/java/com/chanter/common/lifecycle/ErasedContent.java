package com.chanter.common.lifecycle;

import java.util.*;

/** Exact owning content identities, never a public deletion request or new terminal authority. */
public final class ErasedContent {
    public static final String ERASE="ACCOUNT_CONTENT_ERASE", RECEIPT="ACCOUNT_CONTENT_ERASED";
    public static final int PAGE_SIZE=256;
    private static final Map<String,Set<String>> KINDS=Map.of(
            "community",Set.of("ANNOUNCEMENT","EVENT","OFFICE_HOURS"),
            "message",Set.of("MESSAGE","QUESTION","QUESTION_PREVIEW","FAQ"));
    private ErasedContent() { }
    public record Ref(String kind,UUID id) {
        public String orderKey() { return kind+":"+id; }
        public String notificationType() {
            return switch(kind) {
                case "EVENT" -> "COMMUNITY_EVENT";
                case "QUESTION","QUESTION_PREVIEW" -> "SUPPORT_QUESTION";
                default -> kind;
            };
        }
    }
    public record Batch(TerminalJournal.Entry entry,List<Ref> refs) {
        public Batch { if(refs!=null) refs=List.copyOf(refs); }
        public void validate(String owner) {
            if(entry==null || refs==null || refs.isEmpty() || refs.size()>PAGE_SIZE || owner==null || !KINDS.containsKey(owner)) throw invalid();
            entry.validate();
            if(!entry.targetKind().equals("ACCOUNT")) throw invalid();
            String previous="";
            for(var ref:refs) {
                if(ref==null || ref.id()==null || ref.kind()==null || !KINDS.get(owner).contains(ref.kind()) || previous.compareTo(ref.orderKey())>=0) throw invalid();
                previous=ref.orderKey();
            }
        }
        public String key(UUID commandId) {
            if(commandId==null || entry==null) throw invalid();
            return "ACCOUNT_CONTENT:"+entry.eventId()+":"+commandId;
        }
    }
    public record Receipt(String owner,UUID commandId,Batch batch) {
        public void validate() { if(commandId==null || batch==null) throw invalid(); batch.validate(owner); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid erased content command"); }
}
