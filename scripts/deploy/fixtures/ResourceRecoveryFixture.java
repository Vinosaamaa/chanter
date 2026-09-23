import com.chanter.media.application.PrivateResourceStorage;
import com.chanter.media.application.ResourceRecoveryInventory;
import com.chanter.media.application.StorageMutationStore;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;

/** Unshipped hosted fixture: invokes the real fenced source and adapter, never fabricates an inventory. */
public final class ResourceRecoveryFixture {
    private static final int LIMIT=16*1024;
    public static void main(String[] args) throws Exception {
        if(args.length!=0 || !"true".equals(System.getenv("CHANTER_SOURCE_RECOVERY_PREVIEW")))
            throw new IllegalStateException("Hosted object recovery fixture only");
        byte[] input=System.in.readNBytes(LIMIT+1);
        if(input.length>LIMIT) throw new IllegalArgumentException("Fixture input too large");
        var output=System.out; System.setOut(System.err);
        Class<?> application=Class.forName(Files.readString(Path.of("/app/main-class")).trim());
        try(var context=new SpringApplication(application).run("--server.port=0","--management.server.port=0",
                "--spring.main.banner-mode=off","--chanter.recovery-mode=true","--chanter.media.recovery-inventory-enabled=true",
                "--spring.flyway.enabled=false","--chanter.errors.enabled=false","--chanter.telemetry.enabled=false")) {
            if(!"media-service".equals(context.getEnvironment().getProperty("spring.application.name")))
                throw new IllegalStateException("Media source required");
            var mapper=context.getBean(ObjectMapper.class);
            JsonNode request=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(input);
            if(request==null || !request.isObject()) throw new IllegalArgumentException("Fixture object required");
            var inventories=context.getBean(ResourceRecoveryInventory.class);
            var mutations=context.getBean(StorageMutationStore.class);
            var storage=context.getBean(PrivateResourceStorage.class);
            String action=text(request,"action");
            Object result=switch(action) {
                case "fence" -> {
                    fields(request,"action","inventoryId");
                    yield mutations.fence(UUID.fromString(text(request,"inventoryId")));
                }
                case "capture" -> {
                    fields(request,"action","inventoryId","databaseBackupId","authority");
                    yield inventories.capture(UUID.fromString(text(request,"inventoryId")),UUID.fromString(text(request,"databaseBackupId")),
                            decode(mapper,request.get("authority"),ResourceRecoveryInventory.Authority.class));
                }
                case "page" -> {
                    fields(request,"action","inventoryId","authority","after","limit");
                    yield inventories.page(UUID.fromString(text(request,"inventoryId")),
                            decode(mapper,request.get("authority"),ResourceRecoveryInventory.Authority.class),number(request,"after"),number(request,"limit"));
                }
                case "discard" -> {
                    fields(request,"action","inventoryId");UUID inventory=UUID.fromString(text(request,"inventoryId"));
                    inventories.discard(inventory);yield mutations.receipt(inventory);
                }
                case "reapply" -> {
                    fields(request,"action","page");
                    // Fixed owning #251 controller contract, absent from the pre-union baseline.
                    Class<?> controller=Class.forName("com.chanter.common.lifecycle.SourceTerminalRecoveryController");
                    var response=(org.springframework.http.ResponseEntity<?>)controller.getMethod("reapply",String.class,String.class)
                            .invoke(context.getBean(controller),context.getEnvironment().getRequiredProperty("chanter.internal-service-token"),mapper.writeValueAsString(request.get("page")));
                    yield response.getBody();
                }
                case "delete" -> {
                    fields(request,"action","request");
                    var deletion=decode(mapper,request.get("request"),ResourceRecoveryInventory.RestoreRequest.class);
                    storage.deleteForRecovery(deletion);
                    if(!inventories.beginDelete(storage.backend(),deletion).alreadyClosed()) throw new IllegalStateException("Physical closure is absent");
                    yield Map.of("physicallyClosed",true,"outstandingMutations",mutations.receipt(deletion.inventoryId()).unsettledMutations(),"publicCutoverAllowed",false);
                }
                case "finish-delete" -> {
                    fields(request,"action","request","resourceId");
                    var deletion=decode(mapper,request.get("request"),ResourceRecoveryInventory.RestoreRequest.class);
                    UUID resource=UUID.fromString(text(request,"resourceId"));
                    var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                    tx.setTimeout(30);
                    tx.executeWithoutResult(status -> {
                        var proof=inventories.requireClosedDeletionLocked(deletion.inventoryId(),deletion.databaseBackupId(),deletion.authority(),resource);
                        try {
                            Class<?> tuple=Class.forName("com.chanter.media.application.ResourceLifecycle$MaintenanceDeletion");
                            Object value=tuple.getConstructor(UUID.class,String.class,String.class,String.class,long.class,String.class)
                                    .newInstance(proof.resourceId(),proof.storageBackend(),proof.currentKey(),proof.migrationKey(),proof.byteSize(),proof.sha256());
                            var lifecycle=context.getBean(com.chanter.media.application.ResourceLifecycle.class);
                            lifecycle.getClass().getMethod("finishVerifiedMaintenanceDelete",tuple).invoke(lifecycle,value);
                        } catch(ReflectiveOperationException unavailable) { throw new IllegalStateException("Owning source completion failed",unavailable); }
                    });
                    var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
                    Map<String,Object> completed=new LinkedHashMap<>(jdbc.queryForObject("SELECT state,byte_reservation FROM course_resources WHERE id=?",
                            (rs,n) -> Map.<String,Object>of("state",rs.getString(1),"sourceRetained",rs.getBoolean(2),"publicCutoverAllowed",false),resource));
                    completed.put("reservedBytes",jdbc.queryForObject("SELECT reserved_bytes FROM media_storage_budget WHERE id=1",Long.class));
                    yield completed;
                }
                case "expect-refusal" -> {
                    fields(request,"action","request","base64","reason");
                    var restore=decode(mapper,request.get("request"),ResourceRecoveryInventory.RestoreRequest.class);
                    String reason=text(request,"reason");
                    byte[] bytes=bytes(request);
                    boolean refused=false;
                    try { storage.putForRecovery(restore,bytes); }
                    catch(PrivateResourceStorage.PutFailure failure) {
                        refused=switch(reason) {
                            case "CORRUPT_CONTENT" -> failure.outcome()==PrivateResourceStorage.WriteOutcome.NOT_STARTED
                                    && failure.getCause() instanceof IllegalArgumentException;
                            case "OBJECT_EXISTS" -> failure.outcome()==PrivateResourceStorage.WriteOutcome.FINISHED
                                    && failure.getCause() instanceof FileAlreadyExistsException;
                            case "STALE_SOURCE" -> failure.outcome()==PrivateResourceStorage.WriteOutcome.NOT_STARTED
                                    && failure.getCause() instanceof IllegalStateException;
                            default -> false;
                        };
                        if(!refused) throw failure;
                    }
                    if(!refused) throw new IllegalStateException("Expected owning refusal was not returned");
                    yield Map.of("refused",reason,"publicCutoverAllowed",false);
                }
                case "read", "restore" -> {
                    if(action.equals("read")) fields(request,"action","request"); else fields(request,"action","request","base64");
                    var restore=decode(mapper,request.get("request"),ResourceRecoveryInventory.RestoreRequest.class);
                    var before=reference(inventories,restore);
                    // Fixture bytes are deliberately small; this is not a new production export interface.
                    if(before.byteSize()<1 || before.byteSize()>1024) throw new IllegalArgumentException("Fixture byte limit");
                    if(action.equals("restore")) {
                        storage.putForRecovery(restore,bytes(request));
                    }
                    byte[] bytes;
                    try(var stream=storage.open(before.key())) { bytes=stream.readNBytes(1025); }
                    PrivateResourceStorage.verifyRecoveryBytes(before,bytes);
                    if(!before.equals(reference(inventories,restore))) throw new IllegalStateException("Fixture source changed during read-back");
                    yield Map.of("resourceId",before.resourceId(),"byteSize",bytes.length,"sha256",before.sha256(),
                            "base64",Base64.getEncoder().encodeToString(bytes),"publicCutoverAllowed",false);
                }
                default -> throw new IllegalArgumentException("Unknown fixture action");
            };
            byte[] encoded=mapper.writeValueAsBytes(result);
            if(encoded.length>64*1024) throw new IllegalStateException("Fixture result too large");
            output.write(encoded); output.println();
        }
    }
    private static byte[] bytes(JsonNode request) {
        String encoded=text(request,"base64");
        byte[] bytes=Base64.getDecoder().decode(encoded);
        if(!Base64.getEncoder().encodeToString(bytes).equals(encoded) || bytes.length<1 || bytes.length>1024)
            throw new IllegalArgumentException("Invalid fixture bytes");
        return bytes;
    }
    private static ResourceRecoveryInventory.Reference reference(ResourceRecoveryInventory inventory,ResourceRecoveryInventory.RestoreRequest request) {
        var page=inventory.page(request.inventoryId(),request.authority(),request.ordinal()-1,1);
        if(!page.snapshot().databaseBackupId().equals(request.databaseBackupId()) || page.references().size()!=1)
            throw new IllegalStateException("Fixture inventory identity mismatch");
        var reference=page.references().getFirst();
        if(reference.ordinal()!=request.ordinal() || reference.terminal() || !reference.sourceRetained()
                || !Set.of("AVAILABLE","QUARANTINED","SCAN_FAILED").contains(reference.resourceState()))
            throw new IllegalStateException("Fixture object is not restorable");
        return reference;
    }
    private static <T> T decode(ObjectMapper mapper,JsonNode value,Class<T> type) throws Exception {
        return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).with(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .readValue(value);
    }
    private static void fields(JsonNode node,String... names) {
        Set<String> expected=Set.of(names); var actual=new HashSet<String>(); node.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected)) throw new IllegalArgumentException("Unexpected fixture fields");
    }
    private static String text(JsonNode value,String field) {
        JsonNode node=value.get(field);
        if(node==null || !node.isTextual() || node.textValue().length()>1400) throw new IllegalArgumentException("Invalid fixture field");
        return node.textValue();
    }
    private static int number(JsonNode value,String field) {
        JsonNode node=value.get(field);
        if(node==null || !node.isIntegralNumber() || !node.canConvertToInt()) throw new IllegalArgumentException("Invalid fixture number");
        return node.intValue();
    }
}
