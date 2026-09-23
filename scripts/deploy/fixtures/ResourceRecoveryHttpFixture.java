import com.chanter.media.application.ResourceLifecycle;
import com.chanter.media.application.ResourceRecoveryObjects;
import java.nio.file.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;

/** Unshipped dependency fixture. The source completion hook is absent until the reviewed source union. */
public final class ResourceRecoveryHttpFixture {
    public static void main(String[] args) throws Exception {
        if(args.length!=0 || !"true".equals(System.getenv("CHANTER_SOURCE_RECOVERY_PREVIEW")))
            throw new IllegalStateException("Hosted recovery transport fixture only");
        Class<?> application=Class.forName(Files.readString(Path.of("/app/main-class")).trim());
        var context=new SpringApplication(application,CompletionConfiguration.class).run(
                "--server.port=8080","--chanter.recovery-mode=true","--chanter.media.recovery-inventory-enabled=true",
                "--spring.flyway.enabled=false","--chanter.errors.enabled=false","--chanter.telemetry.enabled=false");
        if(!"media-service".equals(context.getEnvironment().getProperty("spring.application.name"))) {
            context.close();throw new IllegalStateException("Media source required");
        }
    }
    @Configuration(proxyBeanMethods=false)
    static class CompletionConfiguration {
        @Bean ResourceRecoveryObjects.Completion verifiedSourceCompletion(ResourceLifecycle lifecycle) {
            return proof -> lifecycle.finishVerifiedMaintenanceDelete(new ResourceLifecycle.MaintenanceDeletion(
                    proof.resourceId(),proof.storageBackend(),proof.currentKey(),proof.migrationKey(),proof.byteSize(),proof.sha256()));
        }
    }
}
