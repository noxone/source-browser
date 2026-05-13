package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.FileInfoDto;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetFileContentUseCase;
import com.hlag.sourceviewer.domain.port.incoming.GetFileInfoUseCase;
import com.hlag.sourceviewer.domain.port.incoming.GetTokenStreamUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

@Path("/api/files")
@Authenticated
public class FileResource {

    private final GetTokenStreamUseCase getTokenStreamUseCase;
    private final GetFileInfoUseCase getFileInfoUseCase;
    private final GetFileContentUseCase getFileContentUseCase;

    @Inject
    public FileResource(
            GetTokenStreamUseCase getTokenStreamUseCase,
            GetFileInfoUseCase getFileInfoUseCase,
            GetFileContentUseCase getFileContentUseCase) {
        this.getTokenStreamUseCase = getTokenStreamUseCase;
        this.getFileInfoUseCase = getFileInfoUseCase;
        this.getFileContentUseCase = getFileContentUseCase;
    }

    /**
     * Returns the lexical token stream for the given file as a JSON array.
     * The stored data is gzip-compressed; this endpoint inflates it so the client
     * receives plain JSON. HTTP-level compression (Accept-Encoding: gzip) is handled
     * separately by the container.
     */
    @GET
    @Path("/{fileId}/token-stream")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTokenStream(@PathParam("fileId") Long fileId) {
        var tokenStream = getTokenStreamUseCase.getTokenStream(new FileIdentifier(fileId))
                .orElseThrow(() -> new NotFoundException("No token stream for file: " + fileId));
        try {
            byte[] inflated = inflate(tokenStream.data());
            return Response.ok(inflated, MediaType.APPLICATION_JSON).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress token stream for file: " + fileId, e);
        }
    }

    /**
     * Returns metadata about a source file, including git commit information
     * (last author, commit date, message) and whether a token stream is available.
     */
    @GET
    @Path("/{fileId}/info")
    @Produces(MediaType.APPLICATION_JSON)
    public FileInfoDto getFileInfo(@PathParam("fileId") Long fileId) {
        return getFileInfoUseCase.getFileInfo(new FileIdentifier(fileId))
                .orElseThrow(() -> new NotFoundException("File not found: " + fileId));
    }

    /**
     * Returns the raw text content of a source file read directly from the git
     * repository at the last indexed commit. Used as a fallback when no token
     * stream is available.
     */
    @GET
    @Path("/{fileId}/content")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFileContent(@PathParam("fileId") Long fileId) {
        return getFileContentUseCase.getFileContent(new FileIdentifier(fileId))
                .orElseThrow(() -> new NotFoundException("Content not available for file: " + fileId));
    }

    private static byte[] inflate(byte[] compressed) throws IOException {
        try (var gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed));
             var out = new ByteArrayOutputStream()) {
            gzipIn.transferTo(out);
            return out.toByteArray();
        }
    }
}

