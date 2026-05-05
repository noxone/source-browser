package com.hlag.sourceviewer.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "com.hlag.sourceviewer",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureUnitTest {

    private static final String DOMAIN      = "com.hlag.sourceviewer.domain..";
    private static final String APPLICATION = "com.hlag.sourceviewer.application..";
    private static final String ADAPTER_IN  = "com.hlag.sourceviewer.adapter.incoming..";
    private static final String ADAPTER_OUT = "com.hlag.sourceviewer.adapter.outgoing..";
    private static final String INFRA       = "com.hlag.sourceviewer.infrastructure..";

    // ── 1. Layer dependencies ─────────────────────────────────────────────────

    @ArchTest
    static final ArchRule layer_dependencies_are_respected =
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain")       .definedBy(DOMAIN)
            .layer("Application")  .definedBy(APPLICATION)
            .layer("AdapterIn")    .definedBy(ADAPTER_IN)
            .layer("AdapterOut")   .definedBy(ADAPTER_OUT)
            .layer("Infrastructure").definedBy(INFRA)

            .whereLayer("Domain")       .mayNotAccessAnyLayer()
            .whereLayer("Application")  .mayOnlyAccessLayers("Domain")
            .whereLayer("AdapterIn")    .mayOnlyAccessLayers("Application", "Domain")
            .whereLayer("AdapterOut")   .mayOnlyAccessLayers("Application", "Domain")
            .whereLayer("Infrastructure").mayOnlyAccessLayers(
                    "Application", "Domain", "AdapterIn", "AdapterOut");

    // ── 2. Domain is framework-free ───────────────────────────────────────────

    // JPA (@Entity, @Column, etc.) and Hibernate (@JdbcTypeCode) are intentionally
    // permitted in domain classes per deliberate architectural tradeoff.
    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
        noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.quarkus..",
                "jakarta.ws.rs..",
                "jakarta.inject..",
                "jakarta.enterprise..",
                "org.jooq..",
                "org.eclipse.jgit..",
                "com.fasterxml.jackson..",
                "io.smallrye.."
            )
            .as("Domain classes must not have framework dependencies (JPA/Hibernate permitted)");

    // ── 3. Adapters do not know each other ────────────────────────────────────

    @ArchTest
    static final ArchRule incoming_adapters_do_not_know_outgoing =
        noClasses()
            .that().resideInAPackage(ADAPTER_IN)
            .should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT)
            .as("Incoming adapters must not know outgoing adapters");

    @ArchTest
    static final ArchRule outgoing_adapters_do_not_know_incoming =
        noClasses()
            .that().resideInAPackage(ADAPTER_OUT)
            .should().dependOnClassesThat().resideInAPackage(ADAPTER_IN)
            .as("Outgoing adapters must not know incoming adapters");

    // ── 4. Framework containment ──────────────────────────────────────────────

    @ArchTest
    static final ArchRule jpa_only_in_domain_and_persistence =
        noClasses()
            .that().resideOutsideOfPackages(
                DOMAIN,
                "com.hlag.sourceviewer.adapter.outgoing.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "io.quarkus.hibernate..",
                "org.hibernate..")
            .as("JPA and Hibernate annotations may only appear in domain model and persistence adapters");

    @ArchTest
    static final ArchRule jgit_nur_in_git_adapter =
        noClasses()
            .that().resideOutsideOfPackage(
                "com.hlag.sourceviewer.adapter.outgoing.git..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..")
            .as("JGit may only be used in the Git adapter");

    @ArchTest
    static final ArchRule jaxrs_nur_in_eingehenden_adaptern =
        noClasses()
            .that().resideOutsideOfPackage(ADAPTER_IN)
            .should().dependOnClassesThat().resideInAPackage("jakarta.ws.rs..")
            .as("JAX-RS may only be used in incoming adapters");

    @ArchTest
    static final ArchRule qute_only_in_view_adapter =
        noClasses()
            .that().resideOutsideOfPackage(
                "com.hlag.sourceviewer.adapter.incoming.view..")
            .should().dependOnClassesThat().resideInAPackage("io.quarkus.qute..")
            .as("Qute templates may only be used in the view adapter");

    @ArchTest
    static final ArchRule javaparser_nur_in_domain_und_application =
        noClasses()
            .that().resideOutsideOfPackages(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAPackage("com.github.javaparser..")
            .as("JavaParser may only be used in domain and application");

    @ArchTest
    static final ArchRule jackson_nur_in_adaptern =
        noClasses()
            .that().resideOutsideOfPackages(
                ADAPTER_IN,
                "com.hlag.sourceviewer.adapter.outgoing.jackson..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
            .as("Jackson may only be used in adapters");

    // ── 5. Naming conventions ─────────────────────────────────────────────────

    @ArchTest
    static final ArchRule use_case_interfaces_reside_in_port_incoming =
        classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().beInterfaces()
            .andShould().resideInAPackage("com.hlag.sourceviewer.domain.port.incoming..")
            .as("UseCase classes must be interfaces in domain.port.incoming");

    @ArchTest
    static final ArchRule repository_interfaces_reside_in_port_outgoing =
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().areNotAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
            .and().areNotAnnotatedWith("jakarta.persistence.Entity")
            .should().beInterfaces()
            .andShould().resideInAPackage("com.hlag.sourceviewer.domain.port.outgoing..")
            .as("Repository interfaces must reside in domain.port.outgoing");

    @ArchTest
    static final ArchRule service_classes_not_in_adapters =
        noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("com.hlag.sourceviewer.adapter..")
            .as("Service classes must not reside in adapters");

    @ArchTest
    static final ArchRule resource_classes_in_incoming_adapters =
        classes()
            .that().haveSimpleNameEndingWith("Resource")
            .should().resideInAPackage(ADAPTER_IN)
            .as("Resource classes must reside in incoming adapters");

    @ArchTest
    static final ArchRule dto_classes_in_adapter_dto_packages =
        classes()
            .that().haveSimpleNameEndingWith("Dto")
            .should().resideInAPackage("com.hlag.sourceviewer.adapter.incoming..dto..")
            .as("Data transfer objects must reside in the dto sub-package of incoming adapters");

    @ArchTest
    static final ArchRule mapper_classes_in_persistence_mapping =
        classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .should().resideInAPackage(
                "com.hlag.sourceviewer.adapter.outgoing.persistence.mapping..")
            .as("Mapper classes must reside in adapter.outgoing.persistence.mapping");

    @ArchTest
    static final ArchRule converter_classes_in_persistence_converter =
        classes()
            .that().haveSimpleNameEndingWith("Converter")
            .should().resideInAPackage(
                "com.hlag.sourceviewer.adapter.outgoing.persistence.converter..")
            .as("Converter classes must reside in adapter.outgoing.persistence.converter");

    // ── 6. Logging conventions ────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_system_out_in_production_code =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().callMethod(System.class, "out")
            .as("System.out.println is forbidden — use SLF4J");

    @ArchTest
    static final ArchRule no_system_err_in_production_code =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().callMethod(System.class, "err")
            .as("System.err.println is forbidden — use SLF4J");

    // ── 7. Date API convention ────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_java_util_date =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().dependOnClassesThat().areAssignableTo(java.util.Date.class)
            .as("java.util.Date is forbidden — use java.time.*");

    @ArchTest
    static final ArchRule no_java_util_calendar =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().dependOnClassesThat().areAssignableTo(java.util.Calendar.class)
            .as("java.util.Calendar is forbidden — use java.time.*");

    // ── 8. Threading convention ───────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_direct_thread_creation =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().callConstructor(Thread.class)
            .as("Threads must not be created directly — use ManagedExecutor");

    @ArchTest
    static final ArchRule no_executors_new_fixed_thread_pool =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().callMethod(java.util.concurrent.Executors.class,
                "newFixedThreadPool", int.class)
            .as("Custom thread pools are forbidden — use ManagedExecutor");

    @ArchTest
    static final ArchRule no_executors_new_cached_thread_pool =
        noClasses()
            .that().resideInAPackage("com.hlag.sourceviewer..")
            .should().callMethod(java.util.concurrent.Executors.class,
                "newCachedThreadPool")
            .as("Custom thread pools are forbidden — use ManagedExecutor");

    // ── 9. No cycles ──────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_cycles_between_packages =
        slices()
            .matching("com.hlag.sourceviewer.(*)..")
            .should().beFreeOfCycles()
            .as("No cyclic dependencies between top-level packages are allowed");
}
