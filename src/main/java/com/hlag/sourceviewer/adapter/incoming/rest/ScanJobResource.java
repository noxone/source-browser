package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.ScanJobDto;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ManageScanJobsUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * REST resource for admin management of scan jobs.
 *
 * <p>Exposes endpoints to list all scan jobs (with optional status filter) and
 * to delete individual or all queued jobs.</p>
 */
@Path("/api/admin/scan-jobs")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ScanJobResource {

    private static final Logger logger = LoggerFactory.getLogger(ScanJobResource.class);

    private final ManageScanJobsUseCase manageScanJobsUseCase;

    @Inject
    public ScanJobResource(ManageScanJobsUseCase manageScanJobsUseCase) {
        this.manageScanJobsUseCase = manageScanJobsUseCase;
    }

    /**
     * Lists scan jobs. When {@code status} is provided, only jobs matching that status
     * are returned; otherwise all jobs are returned.
     *
     * @param status optional status filter (QUEUED, RUNNING, DONE, FAILED)
     */
    @GET
    public List<ScanJobDto> listScanJobs(@QueryParam("status") String status) {
        Optional<ScanJob.ScanJobStatus> statusFilter = Optional.ofNullable(status)
                .map(String::toUpperCase)
                .map(ScanJob.ScanJobStatus::valueOf);

        return manageScanJobsUseCase.listScanJobs(statusFilter)
                .stream()
                .map(ScanJobDto::from)
                .toList();
    }

    /**
     * Deletes a single scan job. Only jobs in {@code QUEUED} status can be deleted.
     *
     * @param id the scan job ID
     * @return 204 No Content on success, 404 if not found, 409 if not deletable
     */
    @DELETE
    @Path("/{id}")
    public Response deleteScanJob(@PathParam("id") Long id) {
        logger.info("Deleting scan job {}", id);
        try {
            manageScanJobsUseCase.deleteScanJob(new ScanJobIdentifier(id));
            return Response.noContent().build();
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Deletes all scan jobs currently in {@code QUEUED} status.
     *
     * @return 204 No Content
     */
    @DELETE
    public Response deleteAllQueuedScanJobs() {
        logger.info("Deleting all queued scan jobs");
        manageScanJobsUseCase.deleteAllQueuedScanJobs();
        return Response.noContent().build();
    }
}
