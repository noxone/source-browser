# syntax=docker/dockerfile:1

# ── Stage 1: Build ─────────────────────────────────────────────────────────────
# maven:3.9-eclipse-temurin-21 ships both Maven 3.9 and JDK 21.
# The frontend-maven-plugin downloads Node.js v22.14.0 automatically, so no
# manual Node install is required here.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests --batch-mode --no-transfer-progress


# ── Stage 2: Download JDTLS ────────────────────────────────────────────────────
# Isolated download stage so the runtime image does not need curl.
FROM debian:bookworm-slim AS jdtls-downloader

# renovate: datasource=github-tags depName=eclipse-jdtls/eclipse.jdt.ls versioning=semver
ARG JDTLS_VERSION=1.58.0
# When JDTLS_VERSION is bumped, update JDTLS_QUALIFIER to the build timestamp
# found at https://download.eclipse.org/jdtls/milestones/<version>/
ARG JDTLS_QUALIFIER=202604151538

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    BASE_URL="https://download.eclipse.org/jdtls/milestones/${JDTLS_VERSION}/jdt-language-server-${JDTLS_VERSION}-${JDTLS_QUALIFIER}.tar.gz"; \
    curl -fsSL "${BASE_URL}" -o /tmp/jdtls.tar.gz; \
    curl -fsSL "${BASE_URL}.sha256" -o /tmp/jdtls.sha256; \
    echo "$(cat /tmp/jdtls.sha256)  /tmp/jdtls.tar.gz" | sha256sum --check; \
    mkdir -p /opt/jdtls; \
    tar -xzf /tmp/jdtls.tar.gz -C /opt/jdtls/; \
    rm /tmp/jdtls.tar.gz /tmp/jdtls.sha256


# ── Stage 3: Runtime ───────────────────────────────────────────────────────────
# Full JDK (not JRE) is required because JDTLS runs as a Java subprocess that
# needs the full JDK toolchain to compile and analyse Java source files.
FROM eclipse-temurin:21-jdk-jammy

RUN groupadd -r sourceviewer \
    && useradd -r -g sourceviewer -d /app -s /sbin/nologin sourceviewer

COPY --from=jdtls-downloader /opt/jdtls/ /opt/jdtls/
COPY --from=builder /build/target/quarkus-app/ /app/

RUN chown -R sourceviewer:sourceviewer /app /opt/jdtls

# JDTLS is installed at /opt/jdtls/.  After the first startup configure the
# paths via the admin REST API (requires admin role):
#
#   PUT /api/admin/settings/lsp.jdtls.launcher-jar
#       value: /opt/jdtls/plugins/org.eclipse.equinox.launcher_<version>.jar
#
#   PUT /api/admin/settings/lsp.jdtls.config.linux.x64
#       value: /opt/jdtls/config_linux
#
# Find the exact launcher JAR name with:
#   docker run --rm <image> ls /opt/jdtls/plugins/ | grep equinox.launcher_
ENV JDTLS_HOME=/opt/jdtls

USER sourceviewer
WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
