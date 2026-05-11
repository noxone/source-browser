package com.hlag.sourceviewer.adapter.outgoing.gitlab;

import com.hlag.sourceviewer.adapter.outgoing.git.GitAccessException;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.DiscoveredRepository;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.port.outgoing.GitProviderGroupClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.GroupProjectsFilter;
import org.gitlab4j.api.models.Project;

import java.util.List;

/**
 * {@link GitProviderGroupClient} implementation that uses the gitlab4j library to query
 * the GitLab REST API for group projects.
 */
@ApplicationScoped
public class GitLabGroupClient implements GitProviderGroupClient {

    @Override
    public List<DiscoveredRepository> discoverRepositories(GitProviderGroup group, String apiSecret) {
        String baseUrl = group.baseUrl().map(FilePath::value).orElse("https://gitlab.com");
        try (GitLabApi api = new GitLabApi(baseUrl, apiSecret)) {
            GroupProjectsFilter filter = buildProjectFilter(group);
            List<Project> projects = api.getGroupApi().getProjects(group.groupPath().value(), filter);
            return projects.stream()
                    .filter(p -> !group.isForkedOmitted() || p.getForkedFromProject() == null)
                    .filter(p -> !group.isImportedOmitted() || p.getImportUrl() == null)
                    .map(p -> new DiscoveredRepository(
                            p.getName(),
                            p.getHttpUrlToRepo(),
                            p.getDefaultBranch() != null ? p.getDefaultBranch() : "main"
                    ))
                    .toList();
        } catch (GitLabApiException e) {
            throw new GitAccessException(
                    "GitLab API call failed for group '" + group.groupPath().value() + "': " + e.getMessage(), e);
        }
    }

    private GroupProjectsFilter buildProjectFilter(GitProviderGroup group) {
        GroupProjectsFilter filter = new GroupProjectsFilter()
                .withIncludeSubGroups(Boolean.TRUE);
        if (group.isArchivedOmitted()) {
            filter = filter.withArchived(Boolean.FALSE);
        }
        if (group.isSharedOmitted()) {
            filter = filter.withShared(Boolean.FALSE);
        }
        return filter;
    }
}
