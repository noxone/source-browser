package com.hlag.sourceviewer.adapter.incoming.rest.dto;

import com.hlag.sourceviewer.domain.model.source.ScanJob;

import java.time.Instant;

/**
 * DTO representing a scan job returned by the admin API.
 *
 * @param id                the scan job database ID
 * @param repositoryId      the ID of the repository being scanned
 * @param triggerType       how the scan was triggered (WEBHOOK, CRON, MANUAL)
 * @param commitSha         the specific commit SHA being scanned, or {@code null} for HEAD
 * @param status            the current status (QUEUED, RUNNING, DONE, FAILED)
 * @param queuedAt          when the job was enqueued
 * @param startedAt         when processing began, or {@code null} if not yet started
 * @param finishedAt        when processing completed, or {@code null} if not yet finished
 * @param lastHeartbeatAt   the last heartbeat timestamp, or {@code null} if no heartbeat received yet
 * @param progress          indexing progress as a percentage (0–100)
 */
public record ScanJobDto(
        Long id,
        Long repositoryId,
        String triggerType,
        String commitSha,
        String status,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant lastHeartbeatAt,
        int progress
) {
    public static ScanJobDto from(ScanJob job) {
        return new ScanJobDto(
                job.identifier().value(),
                job.repositoryIdentifier().value(),
                job.triggerType().name(),
                job.commitSha().map(cs -> cs.value()).orElse(null),
                job.status().name(),
                job.queuedAt(),
                job.startedAt().orElse(null),
                job.finishedAt().orElse(null),
                job.lastHeartbeatAt().orElse(null),
                job.progress()
        );
    }
}
