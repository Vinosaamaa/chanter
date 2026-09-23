package com.chanter.common.events;

import java.util.UUID;

/** Agent confirms derived payload and exact answer/status/notification erasure for the original media event. */
public record ResourceDeletionReceipt(UUID resourceId,UUID commandId) {
    public static final String KIND="RESOURCE_DELETE_COMPLETE";
    public ResourceDeletionReceipt {
        if(resourceId==null || commandId==null) throw new IllegalArgumentException("Invalid resource deletion receipt");
    }
    public String key() { return "RESOURCE_DELETE:"+resourceId+":"+commandId; }
}
