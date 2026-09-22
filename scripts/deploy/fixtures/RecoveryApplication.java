import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.util.ReflectionUtils;

/** Hosted-only startup proof using the packaged application's actual resolved bean definitions. */
public final class RecoveryApplication {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CHANTER_RECOVERY_FIXTURE"))) throw new IllegalStateException("Native fixture only");
        Class<?> source = Class.forName(Files.readString(Path.of("/app/main-class")).trim());
        var application = new SpringApplication(source);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            if (!"true".equals(context.getEnvironment().getProperty("chanter.recovery-mode")))
                throw new IllegalStateException("Recovery mode missing");
            for (String name : factory.getBeanDefinitionNames()) {
                Class<?> type = factory.getType(name, false);
                if (type == null) continue;
                for (var method : ReflectionUtils.getAllDeclaredMethods(type)) {
                    if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)
                            || AnnotatedElementUtils.hasAnnotation(method, Schedules.class))
                        throw new IllegalStateException("Ordinary scheduled worker remains during recovery");
                }
            }
            System.out.println("RECOVERY_ORDINARY_WORKERS_ABSENT");
        }));
        application.run(args);
    }
}
