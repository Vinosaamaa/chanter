package com.chanter.agent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Provider work is outside database transactions; only the current generation may publish. */
@Component
@com.chanter.common.recovery.OrdinaryOperation
@Profile("!test")
@EnableScheduling
public class ResourceIngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(ResourceIngestionWorker.class);
    private final ResourceIngestionJobs jobs;
    private final ResourceIngestionSource source;
    private final ResourceIngestionService ingestion;
    private final boolean enabled;

    public ResourceIngestionWorker(ResourceIngestionJobs jobs, ResourceIngestionSource source,
            ResourceIngestionService ingestion, @Value("${chanter.ingestion.worker-enabled:true}") boolean enabled) {
        this.jobs = jobs; this.source = source; this.ingestion = ingestion; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${chanter.ingestion.worker-delay-ms:5000}")
    public void poll() { if (enabled) runOnce(); }

    public void runOnce() {
        jobs.claim().ifPresent(job -> {
            try { ingestion.ingestClaim(job, source.download(job)); }
            catch (RuntimeException unavailable) {
                jobs.failed(job);
                // Provider responses and document content must not enter logs.
                log.warn("Resource extraction deferred resourceId={}", job.resourceId());
            }
        });
    }
}
