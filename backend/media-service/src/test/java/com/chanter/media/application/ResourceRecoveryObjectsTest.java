package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

class ResourceRecoveryObjectsTest {
    private final ResourceRecoveryInventory inventory=mock(ResourceRecoveryInventory.class);
    private final PrivateResourceStorage storage=mock(PrivateResourceStorage.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<ResourceRecoveryObjects.Completion> completion=mock(ObjectProvider.class);
    private final ResourceRecoveryObjects objects=new ResourceRecoveryObjects(inventory,storage,completion,mock(PlatformTransactionManager.class));
    private final ResourceRecoveryInventory.RestoreRequest request=new ResourceRecoveryInventory.RestoreRequest(UUID.randomUUID(),UUID.randomUUID(),
            new ResourceRecoveryInventory.Authority(0,"0".repeat(64)),1);
    private final byte[] bytes="fixture".getBytes(StandardCharsets.UTF_8);
    private ResourceRecoveryInventory.Reference reference() throws Exception {
        return new ResourceRecoveryInventory.Reference(1,UUID.randomUUID(),UUID.randomUUID(),"CURRENT","local","private-key",bytes.length,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),"QUARANTINED",true,false,null);
    }
    @Test void unauthorizedReadNeverOpensStorage() throws Exception {
        when(storage.backend()).thenReturn("local");
        when(inventory.readReference("local",request)).thenThrow(new IllegalStateException("source unready"));
        assertThatThrownBy(() -> objects.read(request)).isInstanceOf(IllegalStateException.class);
        verify(storage,never()).open(anyString());
    }
    @Test void readChecksBytesAndRequalifiesBeforeDisclosure() throws Exception {
        var reference=reference();when(storage.backend()).thenReturn("local");
        when(inventory.readReference("local",request)).thenReturn(reference);
        when(storage.open(reference.key())).thenReturn(new ByteArrayInputStream(bytes));
        assertThat(objects.read(request)).isEqualTo(bytes);
        verify(inventory,times(2)).readReference("local",request);
        reset(inventory);when(inventory.readReference("local",request)).thenReturn(reference).thenThrow(new IllegalStateException("terminal advanced"));
        when(storage.open(reference.key())).thenReturn(new ByteArrayInputStream(bytes));
        assertThatThrownBy(() -> objects.read(request)).isInstanceOf(IllegalStateException.class);
    }
    @Test void corruptOrOversizedBytesNeverLeaveTheService() throws Exception {
        var reference=reference();when(storage.backend()).thenReturn("local");
        when(inventory.readReference("local",request)).thenReturn(reference);
        for(byte[] corrupt:new byte[][] {"changed".getBytes(StandardCharsets.UTF_8),new byte[10*1024*1024+1]}) {
            when(storage.open(reference.key())).thenReturn(new ByteArrayInputStream(corrupt));
            assertThatThrownBy(() -> objects.read(request)).isInstanceOf(java.io.IOException.class);
        }
        verify(inventory,times(2)).readReference("local",request);
    }
    @Test void missingSourceCompletionBindingFailsBeforeAuthorityOrQuotaMutation() {
        var finish=new ResourceRecoveryObjects.FinishRequest(request.inventoryId(),request.databaseBackupId(),request.authority(),UUID.randomUUID());
        assertThatThrownBy(() -> objects.finish(finish)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(inventory,storage);
    }
}
