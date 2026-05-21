-- ── JDK 21 – module overrides ─────────────────────────────────────────────────
-- These entries have longer prefixes than the java./javax. catch-alls in V1_18,
-- so the URL generation logic will always prefer them for the relevant packages.

INSERT INTO javadoc_provider (name, package_prefix, url_template, sort_order) VALUES

-- java.desktop (AWT, Swing, ImageIO, Print, Sound, Beans)
('JDK 21', 'java.awt.',           'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         60),
('JDK 21', 'java.applet.',        'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         61),
('JDK 21', 'java.beans.',         'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         62),
('JDK 21', 'javax.swing.',        'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         63),
('JDK 21', 'javax.imageio.',      'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         64),
('JDK 21', 'javax.print.',        'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         65),
('JDK 21', 'javax.sound.',        'https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         66),
('JDK 21', 'javax.accessibility.','https://docs.oracle.com/en/java/docs/api/java.desktop/{classPath}.html{anchor}',         67),

-- java.logging
('JDK 21', 'java.util.logging.',  'https://docs.oracle.com/en/java/docs/api/java.logging/{classPath}.html{anchor}',         70),

-- java.management
('JDK 21', 'java.lang.management.','https://docs.oracle.com/en/java/docs/api/java.management/{classPath}.html{anchor}',     72),
('JDK 21', 'javax.management.',    'https://docs.oracle.com/en/java/docs/api/java.management/{classPath}.html{anchor}',     73),

-- java.naming
('JDK 21', 'javax.naming.',       'https://docs.oracle.com/en/java/docs/api/java.naming/{classPath}.html{anchor}',          75),

-- java.xml (covers javax.xml.*, org.w3c.dom.*, org.xml.sax.*)
('JDK 21', 'javax.xml.',          'https://docs.oracle.com/en/java/docs/api/java.xml/{classPath}.html{anchor}',             77),
('JDK 21', 'org.w3c.dom.',        'https://docs.oracle.com/en/java/docs/api/java.xml/{classPath}.html{anchor}',             78),
('JDK 21', 'org.xml.sax.',        'https://docs.oracle.com/en/java/docs/api/java.xml/{classPath}.html{anchor}',             79),

-- java.compiler (annotation processing, JSR 269)
('JDK 21', 'javax.annotation.processing.','https://docs.oracle.com/en/java/docs/api/java.compiler/{classPath}.html{anchor}',80),
('JDK 21', 'javax.lang.model.',   'https://docs.oracle.com/en/java/docs/api/java.compiler/{classPath}.html{anchor}',        81),
('JDK 21', 'javax.tools.',        'https://docs.oracle.com/en/java/docs/api/java.compiler/{classPath}.html{anchor}',        82),

-- jdk.compiler (compiler tree API, used by annotation processors / tools)
('JDK 21', 'com.sun.source.',     'https://docs.oracle.com/en/java/docs/api/jdk.compiler/{classPath}.html{anchor}',         83),

-- java.scripting
('JDK 21', 'javax.script.',       'https://docs.oracle.com/en/java/docs/api/java.scripting/{classPath}.html{anchor}',       85),

-- java.prefs
('JDK 21', 'java.util.prefs.',    'https://docs.oracle.com/en/java/docs/api/java.prefs/{classPath}.html{anchor}',           86),

-- java.rmi
('JDK 21', 'java.rmi.',           'https://docs.oracle.com/en/java/docs/api/java.rmi/{classPath}.html{anchor}',             87),

-- java.transaction.xa
('JDK 21', 'javax.transaction.xa.','https://docs.oracle.com/en/java/docs/api/java.transaction.xa/{classPath}.html{anchor}', 88),

-- java.instrument
('JDK 21', 'java.lang.instrument.','https://docs.oracle.com/en/java/docs/api/java.instrument/{classPath}.html{anchor}',     89),

-- java.security.sasl
('JDK 21', 'javax.security.sasl.','https://docs.oracle.com/en/java/docs/api/java.security.sasl/{classPath}.html{anchor}',  90),

-- java.security.jgss
('JDK 21', 'org.ietf.jgss.',                  'https://docs.oracle.com/en/java/docs/api/java.security.jgss/{classPath}.html{anchor}', 91),
('JDK 21', 'javax.security.auth.kerberos.',   'https://docs.oracle.com/en/java/docs/api/java.security.jgss/{classPath}.html{anchor}', 92),

-- jdk.jfr (Java Flight Recorder)
('JDK 21', 'jdk.jfr.',            'https://docs.oracle.com/en/java/docs/api/jdk.jfr/{classPath}.html{anchor}',             93),

-- jdk.jshell
('JDK 21', 'jdk.jshell.',         'https://docs.oracle.com/en/java/docs/api/jdk.jshell/{classPath}.html{anchor}',          94),

-- ── Jakarta EE 10 ──────────────────────────────────────────────────────────────
-- Single catch-all entry for all jakarta.* packages via the Platform API.
('Jakarta EE 10', 'jakarta.',     'https://jakarta.ee/specifications/platform/10/apidocs/{classPath}.html{anchor}',        100),

-- ── MicroProfile + Quarkus extensions ─────────────────────────────────────────
('MicroProfile', 'org.eclipse.microprofile.', 'https://javadoc.io/doc/org.eclipse.microprofile/microprofile/latest/{classPath}.html{anchor}', 110),
('SmallRye',     'io.smallrye.',              'https://javadoc.io/doc/io.smallrye.common/smallrye-common-annotation/latest/{classPath}.html{anchor}', 115),

-- ── Testing ────────────────────────────────────────────────────────────────────
-- JUnit Jupiter (5.x) – more specific than the JUnit 4 catch-all
('JUnit 5', 'org.junit.jupiter.',  'https://javadoc.io/doc/org.junit.jupiter/junit-jupiter-api/latest/{classPath}.html{anchor}',  200),
('JUnit 5', 'org.junit.platform.', 'https://javadoc.io/doc/org.junit.platform/junit-platform-commons/latest/{classPath}.html{anchor}', 201),
-- JUnit 4 catch-all (org.junit.jupiter.* and org.junit.platform.* take precedence)
('JUnit 4', 'org.junit.',          'https://javadoc.io/doc/junit/junit/latest/{classPath}.html{anchor}',                          202),

('Mockito',  'org.mockito.',       'https://javadoc.io/doc/org.mockito/mockito-core/latest/{classPath}.html{anchor}',             210),

-- WireMock 3.x (org.wiremock.*); older 2.x uses com.github.tomakehurst.wiremock.*
('WireMock', 'org.wiremock.',                     'https://javadoc.io/doc/org.wiremock/wiremock/latest/{classPath}.html{anchor}',        215),
('WireMock', 'com.github.tomakehurst.wiremock.',  'https://javadoc.io/doc/com.github.tomakehurst/wiremock/latest/{classPath}.html{anchor}', 216),

('AssertJ',  'org.assertj.',       'https://javadoc.io/doc/org.assertj/assertj-core/latest/{classPath}.html{anchor}',             220),
('ArchUnit',  'com.tngtech.archunit.', 'https://javadoc.io/doc/com.tngtech.archunit/archunit/latest/{classPath}.html{anchor}',   225),

-- CDI-Unit
('CDI-Unit', 'org.jglue.cdiunit.', 'https://javadoc.io/doc/io.github.cdi-unit/cdi-unit/latest/{classPath}.html{anchor}',        230),

-- Testcontainers
('Testcontainers', 'org.testcontainers.', 'https://javadoc.io/doc/org.testcontainers/testcontainers/latest/{classPath}.html{anchor}', 235),

-- Hamcrest (often pulled in by JUnit 4 / Mockito)
('Hamcrest', 'org.hamcrest.',      'https://javadoc.io/doc/org.hamcrest/hamcrest/latest/{classPath}.html{anchor}',             240),

-- ── Logging ────────────────────────────────────────────────────────────────────
('SLF4J',    'org.slf4j.',         'https://javadoc.io/doc/org.slf4j/slf4j-api/latest/{classPath}.html{anchor}',               300),
('Logback',  'ch.qos.logback.',    'https://javadoc.io/doc/ch.qos.logback/logback-classic/latest/{classPath}.html{anchor}',    305),
('Log4j 2',  'org.apache.logging.log4j.', 'https://javadoc.io/doc/org.apache.logging.log4j/log4j-api/latest/{classPath}.html{anchor}', 310),

-- ── Spring Framework ──────────────────────────────────────────────────────────
('Spring Framework', 'org.springframework.', 'https://docs.spring.io/spring-framework/docs/current/javadoc-api/{classPath}.html{anchor}', 350),

-- ── Jackson ───────────────────────────────────────────────────────────────────
-- More-specific entries first; all start with com.fasterxml.jackson.
('Jackson Databind',    'com.fasterxml.jackson.databind.',   'https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/{classPath}.html{anchor}',    400),
('Jackson Annotations', 'com.fasterxml.jackson.annotation.','https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/latest/{classPath}.html{anchor}', 401),
('Jackson Core',        'com.fasterxml.jackson.core.',       'https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/latest/{classPath}.html{anchor}',        402),
('Jackson',             'com.fasterxml.jackson.',            'https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/{classPath}.html{anchor}',    403),

-- ── Apache Commons ────────────────────────────────────────────────────────────
('Commons Lang 3',        'org.apache.commons.lang3.',       'https://javadoc.io/doc/org.apache.commons/commons-lang3/latest/{classPath}.html{anchor}',        450),
('Commons Collections 4', 'org.apache.commons.collections4.','https://javadoc.io/doc/org.apache.commons/commons-collections4/latest/{classPath}.html{anchor}', 451),
('Commons IO',            'org.apache.commons.io.',          'https://javadoc.io/doc/commons-io/commons-io/latest/{classPath}.html{anchor}',                   452),
('Commons Text',          'org.apache.commons.text.',        'https://javadoc.io/doc/org.apache.commons/commons-text/latest/{classPath}.html{anchor}',         453),
('Commons Math 3',        'org.apache.commons.math3.',       'https://javadoc.io/doc/org.apache.commons/commons-math3/latest/{classPath}.html{anchor}',        454),
('Commons Compress',      'org.apache.commons.compress.',    'https://javadoc.io/doc/org.apache.commons/commons-compress/latest/{classPath}.html{anchor}',     455),
('Commons CSV',           'org.apache.commons.csv.',         'https://javadoc.io/doc/org.apache.commons/commons-csv/latest/{classPath}.html{anchor}',          456),
('Commons Configuration', 'org.apache.commons.configuration2.','https://javadoc.io/doc/org.apache.commons/commons-configuration2/latest/{classPath}.html{anchor}', 457),
('Commons Codec',         'org.apache.commons.codec.',       'https://javadoc.io/doc/commons-codec/commons-codec/latest/{classPath}.html{anchor}',             458),
('Commons DBCP 2',        'org.apache.commons.dbcp2.',       'https://javadoc.io/doc/org.apache.commons/commons-dbcp2/latest/{classPath}.html{anchor}',        459),
('Commons Pool 2',        'org.apache.commons.pool2.',       'https://javadoc.io/doc/org.apache.commons/commons-pool2/latest/{classPath}.html{anchor}',        460),

-- ── Hibernate / JPA ──────────────────────────────────────────────────────────
('Hibernate ORM', 'org.hibernate.', 'https://javadoc.io/doc/org.hibernate.orm/hibernate-core/latest/{classPath}.html{anchor}', 500),

-- ── GitLab4J ─────────────────────────────────────────────────────────────────
('GitLab4J', 'org.gitlab4j.',      'https://javadoc.io/doc/org.gitlab4j/gitlab4j-api/latest/{classPath}.html{anchor}',        510),

-- ── Project Reactor ──────────────────────────────────────────────────────────
('Reactor Core',  'reactor.core.',  'https://projectreactor.io/docs/core/release/api/{classPath}.html{anchor}',               520),
('Reactor Netty', 'reactor.netty.', 'https://projectreactor.io/docs/netty/release/api/{classPath}.html{anchor}',              521),

-- ── Vert.x ───────────────────────────────────────────────────────────────────
('Vert.x', 'io.vertx.',            'https://javadoc.io/doc/io.vertx/vertx-core/latest/{classPath}.html{anchor}',              530),

-- ── Netty ────────────────────────────────────────────────────────────────────
('Netty', 'io.netty.',             'https://netty.io/4.1/api/{classPath}.html{anchor}',                                       535),

-- ── Lombok ───────────────────────────────────────────────────────────────────
('Lombok', 'lombok.',              'https://javadoc.io/doc/org.projectlombok/lombok/latest/{classPath}.html{anchor}',         540),

-- ── MapStruct ────────────────────────────────────────────────────────────────
('MapStruct', 'org.mapstruct.',    'https://javadoc.io/doc/org.mapstruct/mapstruct/latest/{classPath}.html{anchor}',          545),

-- ── RxJava 3 ─────────────────────────────────────────────────────────────────
('RxJava 3', 'io.reactivex.rxjava3.', 'https://javadoc.io/doc/io.reactivex.rxjava3/rxjava/latest/{classPath}.html{anchor}', 550),

-- ── Vavr ─────────────────────────────────────────────────────────────────────
('Vavr', 'io.vavr.',               'https://javadoc.io/doc/io.vavr/vavr/latest/{classPath}.html{anchor}',                    555),

-- ── Byte Buddy ───────────────────────────────────────────────────────────────
('Byte Buddy', 'net.bytebuddy.',   'https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/{classPath}.html{anchor}',         560),

-- ── Apache HttpClient 5 ──────────────────────────────────────────────────────
('Apache HttpClient 5', 'org.apache.hc.client5.', 'https://javadoc.io/doc/org.apache.httpcomponents.client5/httpclient5/latest/{classPath}.html{anchor}', 570),
('Apache HttpCore 5',   'org.apache.hc.core5.',   'https://javadoc.io/doc/org.apache.httpcomponents.core5/httpcore5/latest/{classPath}.html{anchor}',    571),

-- ── OkHttp / Retrofit (Square) ───────────────────────────────────────────────
('OkHttp',   'okhttp3.',           'https://square.github.io/okhttp/5.x/okhttp/{classPath}.html{anchor}',                     580),
('Retrofit', 'retrofit2.',         'https://square.github.io/retrofit/2.x/retrofit/{classPath}.html{anchor}',                 581),

-- ── Apache Kafka ─────────────────────────────────────────────────────────────
('Apache Kafka', 'org.apache.kafka.', 'https://kafka.apache.org/documentation/javadoc/{classPath}.html{anchor}',             590),

-- ── Joda Time ────────────────────────────────────────────────────────────────
('Joda Time', 'org.joda.time.',    'https://www.joda.org/joda-time/apidocs/{classPath}.html{anchor}',                        600),

-- ── Google Guice ─────────────────────────────────────────────────────────────
('Google Guice', 'com.google.inject.', 'https://google.github.io/guice/api-docs/latest/javadoc/{classPath}.html{anchor}',   610),

-- ── Weld (CDI Reference Implementation) ──────────────────────────────────────
('Weld', 'org.jboss.weld.',        'https://javadoc.io/doc/org.jboss.weld/weld-core-impl/latest/{classPath}.html{anchor}',   620),

-- ── RESTEasy ─────────────────────────────────────────────────────────────────
('RESTEasy', 'org.jboss.resteasy.','https://javadoc.io/doc/org.jboss.resteasy/resteasy-jaxrs/latest/{classPath}.html{anchor}', 625),

-- ── Micrometer (metrics) ─────────────────────────────────────────────────────
('Micrometer', 'io.micrometer.',   'https://javadoc.io/doc/io.micrometer/micrometer-core/latest/{classPath}.html{anchor}',   630),

-- ── OpenTelemetry ────────────────────────────────────────────────────────────
('OpenTelemetry', 'io.opentelemetry.', 'https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/{classPath}.html{anchor}', 635);
