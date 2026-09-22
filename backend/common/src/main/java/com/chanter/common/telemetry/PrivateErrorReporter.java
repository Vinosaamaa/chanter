package com.chanter.common.telemetry;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only reconstructed error events enter the SDK's bounded asynchronous transport. No global Sentry scope is installed. */
public final class PrivateErrorReporter implements AutoCloseable {
    private final SentryClient client;
    private final Logger logger;
    private final AppenderBase<ILoggingEvent> appender;
    private final AtomicBoolean closed = new AtomicBoolean();

    PrivateErrorReporter(SentryOptions options, LoggerContext context, String service, String release, String environment) {
        client = new SentryClient(options);
        logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new AppenderBase<>() {
            private long windowStarted = System.nanoTime();
            private int reports;
            @Override protected void append(ILoggingEvent log) {
                if (closed.get()) return;
                try {
                    long now = System.nanoTime();
                    if (now - windowStarted >= java.util.concurrent.TimeUnit.MINUTES.toNanos(1)) {
                        windowStarted = now; reports = 0;
                    }
                    // AppenderBase serializes append calls. This bounds each process,
                    // not the account-wide quota across services or restarts.
                    if (reports >= 5) return;
                    var safe = PrivateErrorEvent.from(log, service, release, environment);
                    if (safe != null) { reports++; client.captureEvent(safe, null, null); }
                } catch (RuntimeException unavailable) { /* Reporting must not change the failing request or recurse through logging. */ }
            }
        };
        appender.setContext(context); appender.setName("chanter-private-errors");
    }
    public void start() { if (!closed.get()) { appender.start(); logger.addAppender(appender); } }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        logger.detachAppender(appender); appender.stop(); client.close();
    }
    static SentryOptions options(String dsn) {
        try {
            var uri = URI.create(dsn);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith(".sentry.io")
                    || uri.getUserInfo() == null || !uri.getUserInfo().matches("[a-f0-9]{32}")
                    || !uri.getPath().matches("/[0-9]{1,20}") || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Error reporting requires a valid Sentry HTTPS DSN"); }
        var options = new SentryOptions(); options.setDsn(dsn);
        options.getEventProcessors().clear(); options.getIntegrations().clear();
        options.setEnableExternalConfiguration(false); options.setSendDefaultPii(false);
        options.setAttachServerName(false); options.setAttachThreads(false); options.setAttachStacktrace(false);
        options.setEnableUncaughtExceptionHandler(false); options.setEnableShutdownHook(false);
        options.setSendClientReports(false); options.setMaxBreadcrumbs(0); options.setCacheDirPath(null);
        options.setTracesSampleRate(0.0); options.getLogs().setEnabled(false); options.getMetrics().setEnabled(false);
        options.setMaxQueueSize(32); options.setConnectionTimeoutMillis(1000); options.setReadTimeoutMillis(1000);
        options.setShutdownTimeoutMillis(1500); options.setFlushTimeoutMillis(1500);
        return options;
    }
}
