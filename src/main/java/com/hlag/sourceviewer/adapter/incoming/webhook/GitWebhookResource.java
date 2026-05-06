package com.hlag.sourceviewer.adapter.incoming.webhook;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.ScanTriggerDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.ScanTriggerResponseDto;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ScanRepositoryUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Webhook endpoint for Git push events.
 * Receives notifications from Git servers and queues scan jobs.
 *
 * <p>Supported triggers:
 * <ul>
 *   <li>GitHub Webhooks (X-GitHub-Event: push)</li>
 *   <li>GitLab Webhooks (X-Gitlab-Event: Push Hook)</li>
 *   <li>Manual invocation via the REST API</li>
 * </ul>
 * </p>
 */
@Path("/api/webhook")
public class GitWebhookResource {

    private static final Logger logger = LoggerFactory.getLogger(GitWebhookResource.class);

    private final ScanRepositoryUseCase scanRepositoryUseCase;

    @Inject
    public GitWebhookResource(ScanRepositoryUseCase scanRepositoryUseCase) {
        this.scanRepositoryUseCase = scanRepositoryUseCase;
    }

    /**
     * Receives a manual scan trigger.
     * For webhook integrations, more specific endpoints will be added.
     */
    @POST
    @Path("/trigger")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerScan(ScanTriggerDto request) {
        logger.info("Manual scan trigger for repository {}", request.repositoryId());

        var command = new ScanRepositoryUseCase.ScanCommand(
                new RepositoryIdentifier(request.repositoryId()),
                Optional.ofNullable(request.commitSha()).map(CommitSha::new),
                ScanJob.TriggerType.MANUAL
        );

        var scanJob = scanRepositoryUseCase.enqueueScan(command);

        return Response.accepted()
                .entity(new ScanTriggerResponseDto(scanJob.identifier().value()))
                .build();
    }
}
