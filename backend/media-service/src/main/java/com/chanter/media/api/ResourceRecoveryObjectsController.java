package com.chanter.media.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.media.application.ResourceRecoveryInventory;
import com.chanter.media.application.ResourceRecoveryObjects;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.DataInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/resource-recovery/objects")
@ConditionalOnProperty(name={"chanter.media.recovery-inventory-enabled","chanter.recovery-mode"},havingValue="true")
public class ResourceRecoveryObjectsController {
    private final ResourceRecoveryObjects objects;
    private final InternalEventAccess access;
    private final ObjectMapper mapper;
    public ResourceRecoveryObjectsController(ResourceRecoveryObjects objects,ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.objects=objects;this.access=new InternalEventAccess(token);
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
    @PostMapping("/read")
    public ResponseEntity<byte[]> read(HttpServletRequest request) {
        var body=json(request,ResourceRecoveryInventory.RestoreRequest.class);
        return response(() -> objects.read(body),MediaType.APPLICATION_OCTET_STREAM);
    }
    @PostMapping("/put")
    public ResponseEntity<ResourceRecoveryObjects.Receipt> put(HttpServletRequest request) {
        access.require(request.getHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN));
        ResourceRecoveryInventory.RestoreRequest body;byte[] bytes;
        try {
            var input=new DataInputStream(request.getInputStream());int length=input.readInt();
            if(length<2 || length>4096) throw rejected();
            byte[] metadata=input.readNBytes(length);if(metadata.length!=length)throw rejected();
            body=parse(metadata,ResourceRecoveryInventory.RestoreRequest.class);
            bytes=input.readNBytes(ResourceRecoveryObjects.MAX_BYTES+1);
            if(bytes.length<1 || bytes.length>ResourceRecoveryObjects.MAX_BYTES)throw rejected();
        } catch(IOException invalid) {throw rejected();}
        return response(() -> objects.put(body,bytes),MediaType.APPLICATION_JSON);
    }
    @PostMapping("/delete")
    public ResponseEntity<ResourceRecoveryObjects.Receipt> delete(HttpServletRequest request) {
        var body=json(request,ResourceRecoveryInventory.RestoreRequest.class);
        return response(() -> objects.delete(body),MediaType.APPLICATION_JSON);
    }
    @PostMapping("/finish-delete")
    public ResponseEntity<ResourceRecoveryObjects.Finished> finish(HttpServletRequest request) {
        var body=json(request,ResourceRecoveryObjects.FinishRequest.class);
        return response(() -> objects.finish(body),MediaType.APPLICATION_JSON);
    }
    private <T> T json(HttpServletRequest request,Class<T> type) {
        access.require(request.getHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN));
        try {return parse(request.getInputStream().readNBytes(4097),type);}
        catch(IOException invalid) {throw rejected();}
    }
    private <T> T parse(byte[] bytes,Class<T> type) {
        if(bytes.length>4096)throw rejected();
        try {T result=mapper.readValue(bytes,type);if(result==null)throw rejected();return result;}
        catch(IOException | IllegalArgumentException invalid) {throw rejected();}
    }
    @FunctionalInterface private interface Operation<T> {T get() throws IOException;}
    private static <T> ResponseEntity<T> response(Operation<T> operation,MediaType type) {
        try {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .contentType(type).body(operation.get());}
        catch(IllegalArgumentException | NullPointerException invalid) {throw rejected();}
        catch(IOException | IllegalStateException | DataAccessException | ResponseStatusException unavailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"RESOURCE_RECOVERY_OBJECT_NOT_READY");
        }
    }
    private static ResponseStatusException rejected() {return new ResponseStatusException(HttpStatus.BAD_REQUEST,"RESOURCE_RECOVERY_REQUEST_REJECTED");}
}
