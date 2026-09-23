package com.chanter.media.application;

import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Private backup bytes; no provider/writer closure or public access is implied. */
@Service
@ConditionalOnProperty(name={"chanter.media.recovery-inventory-enabled","chanter.recovery-mode"},havingValue="true")
public class ResourceRecoveryObjects {
    public static final int MAX_BYTES=10*1024*1024;
    private final ResourceRecoveryInventory inventories;
    private final PrivateResourceStorage storage;
    private final ObjectProvider<Completion> completion;
    private final TransactionTemplate tx;
    /** The accepted owning source must bind its real MANDATORY completion hook. No default implementation exists. */
    @FunctionalInterface public interface Completion { void finish(ResourceRecoveryInventory.VerifiedDeletion proof); }
    public record Receipt(int schemaVersion,String operation,ResourceRecoveryInventory.RestoreRequest request) { }
    public record FinishRequest(UUID inventoryId,UUID databaseBackupId,ResourceRecoveryInventory.Authority authority,UUID resourceId) { }
    public record Finished(int schemaVersion,FinishRequest request,boolean sourceAccountingCommitted) { }

    public ResourceRecoveryObjects(ResourceRecoveryInventory inventories,PrivateResourceStorage storage,
            ObjectProvider<Completion> completion,PlatformTransactionManager transactions) {
        this.inventories=inventories;this.storage=storage;this.completion=completion;
        tx=new TransactionTemplate(transactions);tx.setTimeout(30);
    }
    public byte[] read(ResourceRecoveryInventory.RestoreRequest request) throws IOException {
        var reference=inventories.readReference(storage.backend(),request);
        byte[] bytes;
        try(var input=storage.open(reference.key())) { bytes=input.readNBytes(Math.toIntExact(reference.byteSize())+1); }
        if(bytes.length>MAX_BYTES) throw new IOException("Private recovery object exceeds its bound");
        PrivateResourceStorage.verifyRecoveryBytes(reference,bytes);
        if(!reference.equals(inventories.readReference(storage.backend(),request)))
            throw new IllegalStateException("Recovery source changed during read");
        return bytes;
    }
    public Receipt put(ResourceRecoveryInventory.RestoreRequest request,byte[] bytes) throws IOException {
        storage.putForRecovery(request,bytes);
        inventories.readReference(storage.backend(),request);
        return new Receipt(1,"PUT",request);
    }
    public Receipt delete(ResourceRecoveryInventory.RestoreRequest request) throws IOException {
        storage.deleteForRecovery(request);
        // Recheck authority and exact persisted closure; this cannot dispatch another provider operation.
        inventories.requireClosedReference(storage.backend(),request);
        return new Receipt(1,"DELETE",request);
    }
    public Finished finish(FinishRequest request) {
        Completion source=completion.getIfAvailable();
        if(source==null) throw new IllegalStateException("Owning source completion is unavailable");
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Recovery completion must own its transaction");
        return tx.execute(status -> {
            var proof=inventories.requireClosedDeletionLocked(request.inventoryId(),request.databaseBackupId(),request.authority(),request.resourceId());
            source.finish(proof);
            return new Finished(1,request,true);
        });
    }
}
