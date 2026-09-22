package com.chanter.media.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.media.application.ResourceRecoveryInventory;
import com.chanter.media.application.StorageMutationStore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Disabled private source metadata API. It does not assert provider closure or restore bytes. */
@RestController
@RequestMapping("/api/v1/internal/resource-recovery/inventory")
@ConditionalOnProperty(name={"chanter.media.recovery-inventory-enabled","chanter.recovery-mode"},havingValue="true")
public class ResourceRecoveryInventoryController {
    private final StorageMutationStore mutations;
    private final ResourceRecoveryInventory inventories;
    private final InternalEventAccess access;
    private final ObjectMapper mapper;
    public ResourceRecoveryInventoryController(StorageMutationStore mutations,ResourceRecoveryInventory inventories,ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.mutations=mutations; this.inventories=inventories; access=new InternalEventAccess(token);
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
    public record Identity(UUID inventoryId) { }
    public record Capture(UUID inventoryId,UUID databaseBackupId,ResourceRecoveryInventory.Authority authority) { }
    public record Read(UUID inventoryId,ResourceRecoveryInventory.Authority authority,int after,int limit) { }
    public record Discarded(int schemaVersion,UUID inventoryId,boolean snapshotDiscarded,boolean maintenanceReleased) { }

    @PostMapping("/fence")
    public ResponseEntity<StorageMutationStore.Fence> fence(HttpServletRequest request) {
        var body=read(request,Identity.class); return response(() -> mutations.fence(body.inventoryId()));
    }
    @PostMapping("/capture")
    public ResponseEntity<ResourceRecoveryInventory.Snapshot> capture(HttpServletRequest request) {
        var body=read(request,Capture.class);
        return response(() -> inventories.capture(body.inventoryId(),body.databaseBackupId(),body.authority()));
    }
    @PostMapping("/page")
    public ResponseEntity<ResourceRecoveryInventory.Page> page(HttpServletRequest request) {
        var body=read(request,Read.class);
        return response(() -> inventories.page(body.inventoryId(),body.authority(),body.after(),body.limit()));
    }
    @PostMapping("/discard")
    public ResponseEntity<Discarded> discard(HttpServletRequest request) {
        var body=read(request,Identity.class);
        return response(() -> { inventories.discard(body.inventoryId()); return new Discarded(1,body.inventoryId(),true,false); });
    }
    // Releasing maintenance is intentionally not exposed by a recovery endpoint. Resumption remains separately reviewed.
    private <T> T read(HttpServletRequest request,Class<T> type) {
        access.require(request.getHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN));
        try {
            byte[] bytes=request.getInputStream().readNBytes(4097);
            if(bytes.length>4096) throw rejected();
            T result=mapper.readValue(bytes,type);
            if(result==null) throw rejected();
            return result;
        } catch(IOException | IllegalArgumentException failure) { throw rejected(); }
    }
    private static <T> ResponseEntity<T> response(java.util.function.Supplier<T> operation) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(operation.get());
        } catch(IllegalArgumentException | NullPointerException invalid) { throw rejected(); }
        catch(IllegalStateException | DataAccessException unready) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"RESOURCE_RECOVERY_SOURCE_NOT_READY");
        }
    }
    private static ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"RESOURCE_RECOVERY_REQUEST_REJECTED");
    }
}
