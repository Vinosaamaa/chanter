package com.chanter.agent.application;

/** Fetches only the current approved, checksum-matching source named by a durable job. */
public interface ResourceIngestionSource {
    byte[] download(ResourceIngestionJobs.Job job);
}
