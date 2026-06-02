package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.StorageDirectoryDto;
import com.hlag.sourceviewer.application.storage.AppDirectoryManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/admin/storage-directories")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class StorageDirectoriesResource {

    private final AppDirectoryManager appDirectoryManager;

    @Inject
    public StorageDirectoriesResource(AppDirectoryManager appDirectoryManager) {
        this.appDirectoryManager = appDirectoryManager;
    }

    @GET
    public List<StorageDirectoryDto> listDirectories() {
        return appDirectoryManager.getAllDirectories()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new StorageDirectoryDto(entry.getKey(), entry.getValue().toString()))
                .toList();
    }
}
