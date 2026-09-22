package com.chanter.common.telemetry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/** Construct a fresh event; never pass the source Throwable, log arguments or request context to the SDK. */
final class PrivateErrorEvent {
    private static final Set<String> SERVICES = Set.of("gateway-service", "auth-service", "community-service", "message-service",
            "realtime-service", "media-service", "agent-service", "analytics-service", "search-service", "notification-service");
    private PrivateErrorEvent() { }
    static SentryEvent from(ILoggingEvent log, String service, String release, String environment) {
        if (log.getLevel() == null || !log.getLevel().isGreaterOrEqual(Level.ERROR) || log.getThrowableProxy() == null) return null;
        var errors = new ArrayList<SentryException>();
        var failure = log.getThrowableProxy();
        for (int depth = 0; failure != null && depth < 4; depth++, failure = failure.getCause()) {
            var frames = new ArrayList<SentryStackFrame>();
            var stack = failure.getStackTraceElementProxyArray();
            if (stack != null) for (int index = 0; index < Math.min(64, stack.length) && frames.size() < 12; index++) {
                var source = stack[index].getStackTraceElement();
                if (!source.getClassName().startsWith("com.chanter.") || !identifier(source.getClassName())
                        || !identifier(source.getMethodName()) || source.getLineNumber() < 1) continue;
                var frame = new SentryStackFrame();
                frame.setModule(source.getClassName()); frame.setFunction(source.getMethodName());
                frame.setLineno(source.getLineNumber()); frame.setInApp(true);
                // A log's filename can contain an absolute path. The known class/module and line are sufficient.
                frames.add(frame);
            }
            if (frames.isEmpty()) continue;
            Collections.reverse(frames);
            var error = new SentryException();
            String type = failure.getClassName();
            error.setType(identifier(type) && (type.startsWith("java.") || type.startsWith("javax.")
                    || type.startsWith("org.springframework.") || type.startsWith("com.chanter.")) ? type : "ApplicationFailure");
            error.setStacktrace(new SentryStackTrace(frames)); errors.add(error);
        }
        if (errors.isEmpty()) return null;
        Collections.reverse(errors);
        var event = new SentryEvent(); event.setPlatform("java"); event.setLevel(SentryLevel.ERROR);
        event.setRelease(release != null && release.matches("[a-f0-9]{40}") ? release : "unversioned");
        event.setEnvironment(environment != null && Set.of("production", "staging", "test", "development").contains(environment) ? environment : "unknown");
        event.setTag("service", service != null && SERVICES.contains(service) ? service : "unknown-service");
        event.setExceptions(errors);
        return event;
    }
    private static boolean identifier(String value) { return value != null && value.matches("[A-Za-z_$][A-Za-z0-9_.$]{0,179}"); }
}
