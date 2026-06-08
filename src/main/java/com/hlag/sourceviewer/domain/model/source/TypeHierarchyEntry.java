package com.hlag.sourceviewer.domain.model.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An extends or implements relationship between two types, extracted during indexing. */
@Entity
@Table(name = "type_hierarchy")
public class TypeHierarchyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subtype_fqn", nullable = false, length = 500)
    private String subtypeFqn;

    @Column(name = "supertype_fqn", nullable = false, length = 500)
    private String supertypeFqn;

    /** EXTENDS or IMPLEMENTS */
    @Column(name = "relationship_kind", nullable = false, length = 20)
    private String relationshipKind;

    @Column(name = "scan_job_id")
    private Long scanJobId;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    protected TypeHierarchyEntry() {}

    public TypeHierarchyEntry(String subtypeFqn, String supertypeFqn, String relationshipKind) {
        this.subtypeFqn = subtypeFqn;
        this.supertypeFqn = supertypeFqn;
        this.relationshipKind = relationshipKind;
    }

    public void markUnpublished(Long scanJobId) {
        this.scanJobId = scanJobId;
        this.published = false;
    }

    public Long id() { return id; }
    public String subtypeFqn() { return subtypeFqn; }
    public String supertypeFqn() { return supertypeFqn; }
    public String relationshipKind() { return relationshipKind; }
    public Long scanJobId() { return scanJobId; }
    public boolean published() { return published; }
}
