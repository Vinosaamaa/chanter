import com.chanter.common.events.DurableEvent;
import com.chanter.common.lifecycle.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Unshipped hosted fixture. Seeds synthetic source data, then calls the real owning deletion paths. */
public final class CanonicalLifecycleFixture {
    private static final String PASSWORD="Synthetic lifecycle fixture password 251!";
    private static final int INPUT_LIMIT=65536,OUTPUT_LIMIT=1048576;
    private final ConfigurableApplicationContext context;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final String source;
    private CanonicalLifecycleFixture(ConfigurableApplicationContext context) {
        this.context=context; mapper=context.getBean(ObjectMapper.class); jdbc=context.getBean(JdbcTemplate.class);
        source=context.getEnvironment().getRequiredProperty("spring.application.name").replace("-service","");
        if(!Set.of("auth","community","media","message","agent","search","notification").contains(source))
            throw new IllegalStateException("Canonical fixture requires an owning lifecycle source");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=0 || !"true".equals(System.getenv("CHANTER_CANONICAL_LIFECYCLE_FIXTURE")))
            throw new IllegalStateException("Hosted canonical fixture only");
        byte[] input=System.in.readNBytes(INPUT_LIMIT+1);
        if(input.length>INPUT_LIMIT) throw new IllegalArgumentException("Fixture input too large");
        var output=System.out; System.setOut(System.err); // Spring logs cannot corrupt the bounded machine response.
        Class<?> application=Class.forName(Files.readString(Path.of("/app/main-class")).trim());
        try(var context=new SpringApplication(application).run(
                "--server.port=0","--management.server.port=0","--spring.main.banner-mode=off",
                "--chanter.events.dispatch-enabled=false","--chanter.email.worker-enabled=false",
                "--chanter.media.worker-enabled=false","--chanter.ingestion.worker-enabled=false")) {
            if(context.getEnvironment().getProperty("chanter.recovery-mode",Boolean.class,false))
                throw new IllegalStateException("Canonical allocation requires ordinary source mode");
            var fixture=new CanonicalLifecycleFixture(context);
            JsonNode request=fixture.mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(input);
            byte[] result=fixture.mapper.writeValueAsBytes(fixture.execute(request));
            if(result.length>OUTPUT_LIMIT) throw new IllegalStateException("Fixture output too large");
            output.write(result); output.println();
        }
    }
    Object execute(JsonNode request) throws Exception {
        if(request==null || !request.isObject()) throw new IllegalArgumentException("Fixture object required");
        String action=text(request,"action");
        return switch(action) {
            case "auth-seed" -> {
                requireSource("auth"); fields(request,"action","alias");
                UUID alias=id(request,"alias");
                if(context.getEnvironment().getProperty("chanter.auth.require-email-verification",Boolean.class,false))
                    throw new IllegalStateException("Synthetic fixture requires explicit email-verification-disabled configuration");
                Object result=invoke(bean("com.chanter.auth.application.AuthSessionService"),"registerWithStatus",email(alias),PASSWORD,"Synthetic lifecycle fixture","Hosted fixture");
                Object session=invoke(result,"session");
                if(session==null) throw new IllegalStateException("Fixture alias already exists");
                yield Map.of("alias",alias,"accountId",invoke(invoke(session,"user"),"id"));
            }
            case "account-prepare", "account-confirm" -> {
                requireSource("auth"); fields(request,"action","alias","jobId");
                Object session=invoke(bean("com.chanter.auth.application.AuthSessionService"),"login",email(id(request,"alias")),PASSWORD,"Hosted fixture");
                String authorization="Bearer "+invoke(session,"accessToken");
                Object jobs=bean("com.chanter.auth.lifecycle.AccountDeletionJobs");
                if(action.equals("account-prepare")) {
                    Object prepared=invoke(jobs,"prepare",authorization,id(request,"jobId"));
                    yield invoke(prepared,"job"); // Never export the receipt credential or session tokens.
                }
                yield invoke(jobs,"confirm",authorization,id(request,"jobId"));
            }
            case "community-seed" -> {
                requireSource("community"); fields(request,"action","ownerId"); UUID owner=id(request,"ownerId");
                Class<?> kind=Class.forName("com.chanter.community.domain.StudyServerType");
                Object server=invoke(bean("com.chanter.community.application.StudyServerService"),"createStudyServer",
                        "Synthetic lifecycle server","Hosted canonical fixture",kind.getField("PERSONAL").get(null),List.of(),owner);
                UUID serverId=(UUID)invoke(server,"id");
                Object course=invoke(bean("com.chanter.community.application.CourseService"),"createCourse",serverId,owner,
                        "Synthetic lifecycle course","Hosted canonical fixture","Synthetic cohort");
                yield Map.of("serverId",serverId,"courseId",invoke(course,"id"),"channels",invoke(course,"channels"));
            }
            case "server-delete" -> {
                requireSource("community"); fields(request,"action","serverId","ownerId");
                yield invoke(bean("com.chanter.community.application.StudyServerService"),"deleteStudyServer",id(request,"serverId"),id(request,"ownerId"));
            }
            case "community-history-remove-course" -> {
                requireSource("community"); fields(request,"action","serverId","courseId","ownerId");
                UUID server=id(request,"serverId"),course=id(request,"courseId"),owner=id(request,"ownerId");
                var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                tx.setTimeout(30);
                yield tx.execute(status -> {
                    var terminal=context.getBean(TerminalReapplyStore.class);
                    terminal.requireWritable("ACCOUNT",owner);terminal.requireWritable("STUDY_SERVER",server);
                    context.getBean(SourceDeletionRequests.class).requireOpen(server);
                    var matched=jdbc.query("""
                        SELECT c.id FROM courses c JOIN study_servers s ON s.id=c.study_server_id
                        WHERE c.id=? AND s.id=? AND c.instructor_user_id=? AND s.owner_user_id=?
                          AND c.title='Synthetic lifecycle course' AND s.name='Synthetic lifecycle server'
                        FOR UPDATE
                        """,(rs,n)->rs.getObject(1,UUID.class),course,server,owner,owner);
                    if(matched.size()!=1) throw new IllegalArgumentException("Historical fixture parent mismatch");
                    var channels=jdbc.query("SELECT id FROM course_channels WHERE course_id=? ORDER BY id LIMIT 65",(rs,n)->rs.getObject(1,UUID.class),course);
                    if(channels.isEmpty() || channels.size()>64) throw new IllegalArgumentException("Historical fixture channel bound");
                    if(jdbc.update("DELETE FROM courses WHERE id=? AND study_server_id=?",course,server)!=1)
                        throw new IllegalStateException("Historical fixture course changed");
                    return Map.of("serverId",server,"courseId",course,"channelIds",channels,"historicalFixtureOnly",true);
                });
            }
            case "media-seed" -> {
                requireSource("media"); fields(request,"action","resourceId","courseId","serverId","ownerId");
                UUID resource=id(request,"resourceId");
                var value=mapper.createObjectNode(); value.put("id",resource.toString()); value.put("courseId",id(request,"courseId").toString());
                value.put("studyServerId",id(request,"serverId").toString()); value.put("uploadedByUserId",id(request,"ownerId").toString());
                value.put("title","Synthetic lifecycle resource"); value.put("fileName","fixture.txt"); value.put("contentType","text/plain");
                value.put("byteSize",1); value.put("storageKey",resource.toString()); value.put("aiApproved",false);
                value.put("createdAt",Instant.now().toString()); value.put("state","STAGING"); value.put("sha256","a".repeat(64));
                value.put("idempotencyKey",resource.toString()); value.put("storageBackend","S3"); value.put("ingestionStatus","NONE"); value.putArray("ingestionSignals");
                Object record=mapper.treeToValue(value,Class.forName("com.chanter.media.domain.CourseResource"));
                invoke(bean("com.chanter.media.application.ResourceLifecycle"),"reserve",record);
                // No physical write is claimed. This row must remain unsettled/PENDING in recovery.
                yield Map.of("resourceId",resource,"storageWriteSettled",false);
            }
            case "media-upload" -> {
                requireSource("media"); fields(request,"action","courseId","ownerId","requestId");
                Object resource=invoke(bean("com.chanter.media.application.CourseResourceService"),"uploadCourseResource",
                        id(request,"courseId"),id(request,"ownerId"),"Synthetic recovery bytes",false,new FixtureUpload(),id(request,"requestId"),null);
                yield resourceState(resource);
            }
            case "media-work-once" -> {
                requireSource("media"); fields(request,"action","resourceId"); UUID resourceId=id(request,"resourceId");
                Object lifecycle=bean("com.chanter.media.application.ResourceLifecycle");
                Object before=((Optional<?>)invoke(lifecycle,"find",resourceId)).orElseThrow();
                if(!"QUARANTINED".equals(invoke(before,"state"))) throw new IllegalStateException("Expected quarantined fixture resource");
                invoke(bean("com.chanter.media.application.ResourceWorker"),"runOnce");
                yield resourceState(((Optional<?>)invoke(lifecycle,"find",resourceId)).orElseThrow());
            }
            case "media-read-fixture" -> {
                requireSource("media"); fields(request,"action","resourceId"); UUID resourceId=id(request,"resourceId");
                Object resource=((Optional<?>)invoke(bean("com.chanter.media.application.ResourceLifecycle"),"find",resourceId)).orElseThrow();
                byte[] expected=new FixtureUpload().getBytes();
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expected));
                if(!"AVAILABLE".equals(invoke(resource,"state")) || !Long.valueOf(expected.length).equals(invoke(resource,"byteSize"))
                        || !digest.equals(invoke(resource,"sha256")) || !Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT storage_write_settled FROM course_resources WHERE id=?",Boolean.class,resourceId)))
                    throw new IllegalStateException("Only the settled exact fixture bytes may be read");
                byte[] bytes;
                try(var input=(java.io.InputStream)invoke(bean("com.chanter.media.application.PrivateResourceStorage"),"open",invoke(resource,"storageKey"))) {
                    bytes=input.readNBytes(expected.length+1);
                }
                if(!java.security.MessageDigest.isEqual(expected,bytes)) throw new IllegalStateException("Fixture storage bytes do not match");
                yield Map.of("resourceId",resourceId,"byteSize",bytes.length,"sha256",digest,"base64",Base64.getEncoder().encodeToString(bytes));
            }
            case "resource-delete" -> {
                requireSource("media"); fields(request,"action","resourceId","ownerId");
                yield invoke(bean("com.chanter.media.application.CourseResourceService"),"deleteCourseResource",id(request,"resourceId"),id(request,"ownerId"));
            }
            case "events" -> {
                fields(request,"action","afterRevision"); long after=number(request,"afterRevision");
                yield jdbc.query("SELECT id,revision,kind,aggregate_key,payload,destination FROM durable_outbox WHERE revision>? AND destination LIKE 'lifecycle-%' ORDER BY revision LIMIT 64",
                        (rs,n) -> Map.of("destination",rs.getString(6),"event",new DurableEvent(rs.getObject(1,UUID.class),1,source,rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5))),after);
            }
            case "deliver" -> {
                fields(request,"action","event"); DurableEvent event=mapper.treeToValue(request.get("event"),DurableEvent.class); event.validate();
                if(source.equals("auth")) {
                    Object protocol=context.getBean(AccountDeletionProtocol.class);
                    if(AccountDeletionProtocol.SOURCE_REQUEST.equals(event.kind())) invoke(bean("com.chanter.auth.lifecycle.SourceDeletionJobs"),"request",event);
                    else {
                        var receipt=((AccountDeletionProtocol)protocol).receipt(event);
                        invoke(bean(receipt.targetKind().equals("ACCOUNT") ? "com.chanter.auth.lifecycle.AccountDeletionJobs" : "com.chanter.auth.lifecycle.SourceDeletionJobs"),"accept",event);
                    }
                } else if(ErasedContentDelivery.command(event.kind())) context.getBean(ErasedContentDelivery.class).accept(event);
                else if(ErasedContent.ERASE.equals(event.kind()) || ErasedContent.FINAL.equals(event.kind())) context.getBean(ErasedContentReceiver.class).accept(event);
                else if(DeletedScopeDelivery.command(event.kind())) context.getBean(DeletedScopeDelivery.class).accept(event);
                else context.getBean(AccountDeletionParticipant.class).accept(event);
                yield Map.of("eventId",event.id(),"committed",true);
            }
            case "journal" -> {
                requireSource("auth"); fields(request,"action","afterRevision");
                yield invoke(bean("com.chanter.auth.lifecycle.TerminalJournalStore"),"page",number(request,"afterRevision"),null,64);
            }
            case "current-scope" -> {
                requireSource("community"); fields(request,"action","entry","kind","afterId");
                TerminalJournal.Entry entry=mapper.treeToValue(request.get("entry"),TerminalJournal.Entry.class); entry.validate();
                if(!entry.targetKind().equals("STUDY_SERVER")) throw new IllegalArgumentException("Server authority required");
                yield invoke(bean("com.chanter.community.lifecycle.DeletedStudyServerScope"),"page",entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),text(request,"kind"),id(request,"afterId"),256);
            }
            default -> throw new IllegalArgumentException("Unknown fixture action");
        };
    }
    private Object bean(String name) throws Exception { return context.getBean(Class.forName(name)); }
    private Map<String,Object> resourceState(Object resource) throws Exception {
        UUID id=(UUID)invoke(resource,"id");
        return Map.of("resourceId",id,"courseId",invoke(resource,"courseId"),"storageKey",invoke(resource,"storageKey"),
                "backend",invoke(resource,"storageBackend"),"byteSize",invoke(resource,"byteSize"),"sha256",invoke(resource,"sha256"),
                "state",invoke(resource,"state"),"storageWriteSettled",jdbc.queryForObject("SELECT storage_write_settled FROM course_resources WHERE id=?",Boolean.class,id));
    }
    private static final class FixtureUpload implements org.springframework.web.multipart.MultipartFile {
        private final byte[] bytes="Chanter canonical recovery fixture. These are synthetic private resource bytes.\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "fixture.txt"; }
        public String getContentType() { return "text/plain"; }
        public boolean isEmpty() { return false; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes.clone(); }
        public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        public void transferTo(java.io.File destination) throws java.io.IOException { Files.write(destination.toPath(),bytes); }
    }
    private void requireSource(String expected) { if(!source.equals(expected)) throw new IllegalArgumentException("Wrong fixture source"); }
    private static String email(UUID alias) { return "lifecycle-"+alias+"@example.test"; }
    private static UUID id(JsonNode value,String field) {
        String text=text(value,field); UUID id=UUID.fromString(text);
        if(!id.toString().equals(text)) throw new IllegalArgumentException("Canonical UUID required"); return id;
    }
    private static String text(JsonNode value,String field) {
        JsonNode node=value.get(field); if(node==null || !node.isTextual() || node.textValue().length()>200) throw new IllegalArgumentException("Invalid fixture field"); return node.textValue();
    }
    private static long number(JsonNode value,String field) {
        JsonNode node=value.get(field); if(node==null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue()<0) throw new IllegalArgumentException("Invalid fixture revision"); return node.longValue();
    }
    private static void fields(JsonNode node,String... names) {
        Set<String> expected=Set.of(names); var actual=new HashSet<String>(); node.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected)) throw new IllegalArgumentException("Unexpected fixture fields");
    }
    private static Object invoke(Object target,String name,Object... args) throws Exception {
        if(target==null) throw new IllegalStateException("Missing fixture result");
        var matches=Arrays.stream(target.getClass().getMethods()).filter(method -> method.getName().equals(name) && method.getParameterCount()==args.length)
                .filter(method -> { var types=method.getParameterTypes(); for(int i=0;i<types.length;i++) {
                    Class<?> type=types[i]==long.class ? Long.class : types[i]==int.class ? Integer.class : types[i]==boolean.class ? Boolean.class : types[i];
                    if(args[i]!=null && !type.isInstance(args[i])) return false;
                } return true; }).toList();
        if(matches.size()!=1) throw new IllegalStateException("Ambiguous owning fixture operation");
        try { return matches.getFirst().invoke(target,args); }
        catch(java.lang.reflect.InvocationTargetException failure) {
            if(failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }
}
