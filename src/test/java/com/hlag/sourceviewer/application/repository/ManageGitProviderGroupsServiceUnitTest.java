package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.CreateGitProviderGroupCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.UpdateGitProviderGroupCommand;
import com.hlag.sourceviewer.domain.port.outgoing.GitProviderGroupStore;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManageGitProviderGroupsServiceUnitTest {

    private GitProviderGroupStore store;
    private ManageGitProviderGroupsService service;

    @BeforeEach
    void setUp() {
        store = mock(GitProviderGroupStore.class);
        service = new ManageGitProviderGroupsService(store, mock(RepositoryStore.class));
    }

    // ── listGitProviderGroups ─────────────────────────────────────────────────

    @Test
    void listGitProviderGroups_delegates_to_store() {
        var group = gitLabGroup();
        when(store.findAll()).thenReturn(List.of(group));

        var result = service.listGitProviderGroups();

        assertThat(result).containsExactly(group);
        verify(store).findAll();
    }

    @Test
    void listGitProviderGroups_returns_empty_list_when_none_configured() {
        when(store.findAll()).thenReturn(List.of());

        var result = service.listGitProviderGroups();

        assertThat(result).isEmpty();
    }

    // ── findGitProviderGroup ──────────────────────────────────────────────────

    @Test
    void findGitProviderGroup_returns_group_when_found() {
        var identifier = new GitProviderGroupIdentifier(1L);
        var group = gitLabGroup();
        when(store.findByIdentifier(identifier)).thenReturn(Optional.of(group));

        var result = service.findGitProviderGroup(identifier);

        assertThat(result).contains(group);
    }

    @Test
    void findGitProviderGroup_returns_empty_when_not_found() {
        var identifier = new GitProviderGroupIdentifier(99L);
        when(store.findByIdentifier(identifier)).thenReturn(Optional.empty());

        var result = service.findGitProviderGroup(identifier);

        assertThat(result).isEmpty();
    }

    // ── createGitProviderGroup ────────────────────────────────────────────────

    @Test
    void createGitProviderGroup_persists_and_returns_created_group() {
        var command = new CreateGitProviderGroupCommand(
                new DisplayName("my-group"),
                GitProviderType.GITLAB,
                new GroupPath("my-org/my-group"),
                Optional.empty(),
                true,
                false
        );
        var identifier = new GitProviderGroupIdentifier(1L);
        var createdGroup = gitLabGroup();

        when(store.insert(any())).thenReturn(identifier);
        when(store.findByIdentifier(identifier)).thenReturn(Optional.of(createdGroup));

        var result = service.createGitProviderGroup(command);

        assertThat(result).isEqualTo(createdGroup);
        verify(store).insert(any(GitProviderGroup.class));
        verify(store).findByIdentifier(identifier);
    }

    @Test
    void createGitProviderGroup_throws_when_insert_result_cannot_be_reloaded() {
        var command = new CreateGitProviderGroupCommand(
                new DisplayName("my-group"),
                GitProviderType.GITLAB,
                new GroupPath("my-org"),
                Optional.empty(),
                false,
                false
        );
        var identifier = new GitProviderGroupIdentifier(1L);

        when(store.insert(any())).thenReturn(identifier);
        when(store.findByIdentifier(identifier)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGitProviderGroup(command))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── updateGitProviderGroup ────────────────────────────────────────────────

    @Test
    void updateGitProviderGroup_applies_all_fields() {
        var identifier = new GitProviderGroupIdentifier(1L);
        var existingGroup = gitLabGroup();
        when(store.findByIdentifier(identifier)).thenReturn(Optional.of(existingGroup));

        var command = new UpdateGitProviderGroupCommand(
                identifier,
                new DisplayName("updated-name"),
                GitProviderType.GITHUB,
                new GroupPath("updated-org"),
                Optional.empty(),
                true,
                true
        );

        var result = service.updateGitProviderGroup(command);

        assertThat(result.name().value()).isEqualTo("updated-name");
        assertThat(result.providerType()).isEqualTo(GitProviderType.GITHUB);
        assertThat(result.groupPath().value()).isEqualTo("updated-org");
        assertThat(result.isArchivedOmitted()).isTrue();
        assertThat(result.isForkedOmitted()).isTrue();
        verify(store).update(existingGroup);
    }

    @Test
    void updateGitProviderGroup_throws_when_group_not_found() {
        var identifier = new GitProviderGroupIdentifier(99L);
        when(store.findByIdentifier(identifier)).thenReturn(Optional.empty());

        var command = new UpdateGitProviderGroupCommand(
                identifier,
                new DisplayName("x"),
                GitProviderType.GITLAB,
                new GroupPath("x"),
                Optional.empty(),
                false,
                false
        );

        assertThatThrownBy(() -> service.updateGitProviderGroup(command))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── deleteGitProviderGroup ────────────────────────────────────────────────

    @Test
    void deleteGitProviderGroup_delegates_to_store() {
        var identifier = new GitProviderGroupIdentifier(1L);

        service.deleteGitProviderGroup(identifier);

        verify(store).delete(identifier);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GitProviderGroup gitLabGroup() {
        return new GitProviderGroup(
                new DisplayName("my-gitlab-group"),
                GitProviderType.GITLAB,
                new GroupPath("my-org/my-group"),
                Optional.empty(),
                true,
                false
        );
    }
}
