package com.chanter.auth.moderation;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit local process operation; never a public or internal network bypass. */
@Component
@ConditionalOnProperty(name="chanter.moderation.bootstrap-user")
public class OperatorBootstrap implements ApplicationRunner {
    private final OperatorRoles roles;
    private final ConfigurableApplicationContext context;
    private final Environment environment;
    private final UUID user;
    private final String reason;

    public OperatorBootstrap(OperatorRoles roles, ConfigurableApplicationContext context, Environment environment,
            @Value("${chanter.moderation.bootstrap-user}") UUID user,
            @Value("${chanter.moderation.bootstrap-reason:}") String reason) {
        this.roles = roles; this.context = context; this.environment = environment; this.user = user; this.reason = reason;
    }

    @Override public void run(ApplicationArguments arguments) {
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type")))
            throw new IllegalStateException("Operator bootstrap requires explicit non-web application mode");
        roles.bootstrap(user, reason, UUID.randomUUID());
        System.out.println("Initial platform operator recorded. Complete authenticator enrollment before using operator tools.");
        context.close();
    }
}
