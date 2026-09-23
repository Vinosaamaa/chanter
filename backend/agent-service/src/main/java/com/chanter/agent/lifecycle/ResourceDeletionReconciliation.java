package com.chanter.agent.lifecycle;

import com.chanter.common.events.*;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Exact command receipts use the existing durable dispatcher and wait for all affected answer retractions. */
@Service
public final class ResourceDeletionReconciliation {
    private final JdbcTemplate jdbc;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final AgentResourceCleanup cleanup;
    private final AnswerRetractions answers;
    private final ExportSnapshotStore snapshots;
    public ResourceDeletionReconciliation(JdbcTemplate jdbc,PlatformTransactionManager transactions,DurableOutbox outbox,
            ObjectMapper mapper,AgentResourceCleanup cleanup,AnswerRetractions answers,ExportSnapshotStore snapshots) {
        this.jdbc=jdbc;var tx=new TransactionTemplate(transactions);tx.setTimeout(30);consumer=new DurableConsumer(jdbc,tx);
        this.outbox=outbox;this.mapper=mapper;this.cleanup=cleanup;this.answers=answers;this.snapshots=snapshots;
    }
    public void accept(DurableEvent event,ResourceChanged change) {
        event.validate();change.validate();
        if(!"media".equals(event.producer()) || !"RESOURCE_CHANGED".equals(event.kind()) || !change.deleted()
                || !("RESOURCE:"+change.resourceId()).equals(event.aggregateKey())) throw new IllegalArgumentException("Invalid media deletion");
        var receipt=new ResourceDeletionReceipt(change.resourceId(),event.id());
        var command=new DurableEvent(event.id(),1,event.producer(),event.revision(),event.kind(),receipt.key(),event.payload());
        consumer.apply(command,false,() -> {
            // Keep the ordinary permanent cursor, while allowing a new exact proof after an older terminal delivery.
            consumer.apply(event,true,() -> {});
            snapshots.invalidateRetained();cleanup.erase(change.resourceId());
            jdbc.update("INSERT INTO lifecycle_resource_delete_commands(command_id,resource_id) VALUES (?,?)",event.id(),change.resourceId());
            completeResource(change.resourceId());
        });
    }
    public void completeResource(UUID resource) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Deletion receipt requires transaction");
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        if(answers.resourcePending(resource)) return;
        for(UUID command:jdbc.query("SELECT command_id FROM lifecycle_resource_delete_commands WHERE resource_id=? AND receipt_event_id IS NULL ORDER BY command_id",
                (rs,n)->rs.getObject(1,UUID.class),resource)) {
            var receipt=new ResourceDeletionReceipt(resource,command);
            try {
                UUID event=outbox.append("media",ResourceDeletionReceipt.KIND,receipt.key(),mapper.writeValueAsString(receipt));
                jdbc.update("UPDATE lifecycle_resource_delete_commands SET receipt_event_id=? WHERE command_id=?",event,command);
            } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) {throw new IllegalArgumentException("Invalid resource receipt",invalid);}
        }
    }
}
