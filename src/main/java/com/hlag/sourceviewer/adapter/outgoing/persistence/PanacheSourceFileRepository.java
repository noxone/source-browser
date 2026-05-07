package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.ContentSha;
import com.hlag.sourceviewer.domain.model.source.SourceFile;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSourceFileRepository
        implements SourceFileRepository, PanacheRepositoryBase<SourceFile, Long> {

    @Override
    public Optional<SourceFile> findByIdentifier(FileIdentifier identifier) {
        return findByIdOptional(identifier.value());
    }

    @Override
    public Optional<SourceFile> findByRepositoryAndPath(
            RepositoryIdentifier repositoryIdentifier, BranchName branch, FilePath path) {
        return find("repositoryIdentifier = ?1 and branch = ?2 and path = ?3",
                repositoryIdentifier, branch, path)
                .firstResultOptional();
    }

    @Override
    public List<SourceFile> findByRepository(RepositoryIdentifier repositoryIdentifier, BranchName branch) {
        return list("repositoryIdentifier = ?1 and branch = ?2", repositoryIdentifier, branch);
    }

    @Override
    @Transactional
    public FileIdentifier insert(SourceFile sourceFile) {
        persist(sourceFile);
        return sourceFile.identifier();
    }

    @Override
    @Transactional
    public void updateContentSha(FileIdentifier identifier, ContentSha contentSha) {
        findByIdOptional(identifier.value())
                .orElseThrow(() -> new IllegalArgumentException("SourceFile not found: " + identifier))
                .setContentSha(contentSha);
    }

    @Override
    @Transactional
    public void deleteByIdentifier(FileIdentifier identifier) {
        deleteById(identifier.value());
    }
}
