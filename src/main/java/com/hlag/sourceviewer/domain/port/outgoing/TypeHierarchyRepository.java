package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry;

import java.util.List;

public interface TypeHierarchyRepository {

    List<TypeHierarchyEntry> findSubtypes(String supertypeFqn);

    List<TypeHierarchyEntry> findSupertypes(String subtypeFqn);

    void insertAllUnpublished(List<TypeHierarchyEntry> entries, Long scanJobId);

    void publishByScanJob(Long scanJobId);

    void deleteSupersededByScanJob(Long scanJobId);

    void deleteUnpublishedByScanJob(Long scanJobId);
}
