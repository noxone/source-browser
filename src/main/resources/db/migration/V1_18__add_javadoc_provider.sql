CREATE TABLE javadoc_provider (
    id             BIGSERIAL    PRIMARY KEY,
    name           TEXT         NOT NULL,
    package_prefix TEXT         NOT NULL,
    url_template   TEXT         NOT NULL,
    sort_order     INTEGER      NOT NULL DEFAULT 0
);

INSERT INTO javadoc_provider (name, package_prefix, url_template, sort_order) VALUES
    ('JDK 21',     'java.',                 'https://docs.oracle.com/en/java/docs/api/java.base/{classPath}.html{anchor}',                                   10),
    ('JDK 21',     'javax.',                'https://docs.oracle.com/en/java/docs/api/java.base/{classPath}.html{anchor}',                                   20),
    ('JDK 21',     'java.sql.',             'https://docs.oracle.com/en/java/docs/api/java.sql/{classPath}.html{anchor}',                                    15),
    ('JDK 21',     'javax.sql.',            'https://docs.oracle.com/en/java/docs/api/java.sql/{classPath}.html{anchor}',                                    25),
    ('JDK 21',     'java.net.http.',        'https://docs.oracle.com/en/java/docs/api/java.net.http/{classPath}.html{anchor}',                               16),
    ('Guava',      'com.google.common.',    'https://guava.dev/releases/snapshot-jre/api/docs/{classPath}.html{anchor}',                                     30),
    ('Quarkus',    'io.quarkus.',           'https://javadoc.io/doc/io.quarkus/quarkus-core/latest/{classPath}.html{anchor}',                                40),
    ('JavaParser', 'com.github.javaparser.','https://javadoc.io/doc/com.github.javaparser/javaparser-core/latest/{classPath}.html{anchor}',                 50);
