package com.hlag.sourceviewer.domain.model.javadoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "javadoc_provider")
public class JavadocProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "package_prefix", nullable = false)
    private String packagePrefix;

    @Column(name = "url_template", nullable = false)
    private String urlTemplate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected JavadocProvider() {}

    public JavadocProvider(String name, String packagePrefix, String urlTemplate, int sortOrder) {
        this.name = name;
        this.packagePrefix = packagePrefix;
        this.urlTemplate = urlTemplate;
        this.sortOrder = sortOrder;
    }

    public Long id() { return id; }
    public String name() { return name; }
    public String packagePrefix() { return packagePrefix; }
    public String urlTemplate() { return urlTemplate; }
    public int sortOrder() { return sortOrder; }

    public void setName(String name) { this.name = name; }
    public void setPackagePrefix(String packagePrefix) { this.packagePrefix = packagePrefix; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
