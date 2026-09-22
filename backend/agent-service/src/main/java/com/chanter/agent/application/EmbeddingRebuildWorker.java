package com.chanter.agent.application;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @Profile("!test")
@com.chanter.common.recovery.OrdinaryOperation
public class EmbeddingRebuildWorker {
    private final EmbeddingRebuildJobs jobs;
    private final EmbeddingPipelineService pipeline;
    public EmbeddingRebuildWorker(EmbeddingRebuildJobs jobs,EmbeddingPipelineService pipeline) {this.jobs=jobs;this.pipeline=pipeline;}
    @Scheduled(fixedDelayString="${chanter.embeddings.rebuild-delay-ms:5000}")
    public void runOnce() {
        jobs.expireLeases();
        jobs.claim().ifPresent(job->{
            try {jobs.complete(job,pipeline.prepareFor(job.modelId(),job.snapshot().chunks()));}
            catch(RuntimeException failure){jobs.failed(job);}
        });
    }
}
