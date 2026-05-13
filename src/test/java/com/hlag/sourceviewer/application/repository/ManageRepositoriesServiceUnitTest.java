package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class ManageRepositoriesServiceUnitTest {

    private RepositoryStore repositoryStore;
    private GitAccess gitAccess;
    private ManageRepositoriesService service;

    @BeforeEach
    void setUp() {
        repositoryStore = mock(RepositoryStore.class);
        gitAccess = mock(GitAccess.class);
        service = new ManageRepositoriesService(repositoryStore, gitAccess);
    }

    // ── deleteRepository ──────────────────────────────────────────────────────

    @Test
    void deleteRepository_removes_db_record_and_local_clone() {
        var identifier = new RepositoryIdentifier(1L);
        var repository = testRepository();
        when(repositoryStore.findByIdentifier(identifier)).thenReturn(Optional.of(repository));

        service.deleteRepository(identifier);

        verify(repositoryStore).delete(identifier);
        verify(gitAccess).deleteLocalRepository(repository);
    }

    @Test
    void deleteRepository_skips_local_clone_deletion_when_repo_not_found() {
        var identifier = new RepositoryIdentifier(99L);
        when(repositoryStore.findByIdentifier(identifier)).thenReturn(Optional.empty());

        service.deleteRepository(identifier);

        verify(repositoryStore).delete(identifier);
        verify(gitAccess, never()).deleteLocalRepository(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Repository testRepository() {
        return new Repository(
                new DisplayName("test-repo"),
                Optional.empty(),
                new BranchName("main"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
