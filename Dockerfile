# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build
#   Maven builds the backend; frontend-maven-plugin downloads Node internally
#   and builds the Vue/TypeScript frontend as part of the Maven lifecycle.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# Pre-resolve Maven dependencies to maximise Docker layer cache hits.
# A changed pom.xml invalidates this layer; changed sources do not.
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins -q || true

# Copy sources and run the full build (backend + frontend, tests skipped).
COPY src     ./src
COPY frontend ./frontend
RUN mvn -B package -DskipTests

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime
#   JRE base image, bundled with:
#     • Eclipse JDT Language Server  (Java LSP)
#     • typescript-language-server   (TypeScript LSP)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble

# ── Node.js + TypeScript Language Server ─────────────────────────────────────
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl nodejs npm \
 && npm install -g typescript typescript-language-server \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# ── Eclipse JDT Language Server ──────────────────────────────────────────────
# Pin to a specific release so image builds are reproducible.
# To upgrade: update JDTLS_VERSION + JDTLS_TIMESTAMP from
#   https://download.eclipse.org/jdtls/milestones/
ARG JDTLS_VERSION=1.58.0
ARG JDTLS_TIMESTAMP=202604151538

RUN mkdir -p /opt/jdtls \
 && curl -fsSL \
      "https://www.eclipse.org/downloads/download.php?file=/jdtls/milestones/${JDTLS_VERSION}/jdt-language-server-${JDTLS_VERSION}-${JDTLS_TIMESTAMP}.tar.gz&r=1" \
    | tar -xzC /opt/jdtls \
    # Create a stable symlink to the versioned launcher JAR so the env var
    # below never needs to change when JDTLS is updated.
 && ln -s \
      "$(find /opt/jdtls/plugins -name 'org.eclipse.equinox.launcher_*.jar' | sort | tail -1)" \
      /opt/jdtls/plugins/org.eclipse.equinox.launcher.jar \
    # Prefer the server-side config directory when present (newer releases).
 && ln -s \
      "$([ -d /opt/jdtls/config_ss_linux ] && echo /opt/jdtls/config_ss_linux || echo /opt/jdtls/config_linux)" \
      /opt/jdtls/config_active

# ── Application ───────────────────────────────────────────────────────────────
WORKDIR /app
COPY --from=builder /build/target/quarkus-app ./

# Per-project JDTLS workspace directory (mount a volume here for persistence).
RUN mkdir -p /var/jdtls-workspace

# ── LSP configuration ─────────────────────────────────────────────────────────
ENV JDTLS_LAUNCHER_JAR=/opt/jdtls/plugins/org.eclipse.equinox.launcher.jar
ENV JDTLS_CONFIG_DIR=/opt/jdtls/config_active
ENV JDTLS_DATA_DIR=/var/jdtls-workspace
ENV TYPESCRIPT_LSP_COMMAND=typescript-language-server

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
