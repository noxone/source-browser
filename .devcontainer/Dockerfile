# ─────────────────────────────────────────────────────────────────────────────
# Dev Container Image — Source Viewer
#
# Contains all tools needed for local development:
#   • Eclipse Temurin JDK 25
#   • Apache Maven 3.9
#   • Node.js + npm  (for the Vite/Vue frontend build)
#   • Eclipse JDT Language Server  (JDTLS — for the Java LSP feature)
#   • typescript-language-server   (for the TypeScript LSP feature)
#   • Git, curl, unzip
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-noble

# ── System packages ──────────────────────────────────────────────────────────
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      curl \
      git \
      unzip \
      nodejs \
      npm \
      bash-completion \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# ── Apache Maven ─────────────────────────────────────────────────────────────
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL \
      "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xzC /opt \
 && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# ── TypeScript Language Server ────────────────────────────────────────────────
RUN npm install -g typescript typescript-language-server

# ── Eclipse JDT Language Server ──────────────────────────────────────────────
# Same version as in the production Dockerfile to keep behavior consistent.
ARG JDTLS_VERSION=1.58.0
ARG JDTLS_TIMESTAMP=202604151538

RUN mkdir -p /opt/jdtls \
 && curl -fsSL \
       "https://www.eclipse.org/downloads/download.php?file=/jdtls/milestones/${JDTLS_VERSION}/jdt-language-server-${JDTLS_VERSION}-${JDTLS_TIMESTAMP}.tar.gz&r=1" \
     | tar -xzC /opt/jdtls \
    # Stable symlink to the versioned launcher JAR
 && ln -s \
       "$(find /opt/jdtls/plugins -name 'org.eclipse.equinox.launcher_*.jar' | sort | tail -1)" \
       /opt/jdtls/plugins/org.eclipse.equinox.launcher.jar \
    # Unified config directory (newer releases provide config_ss_linux)
 && ln -s \
       "$([ -d /opt/jdtls/config_ss_linux ] && echo /opt/jdtls/config_ss_linux || echo /opt/jdtls/config_linux)" \
       /opt/jdtls/config_active

# ── JDTLS workspace (persistable via volume) ─────────────────────────────────
RUN mkdir -p /var/jdtls-workspace

# ── Environment variables for LSP configuration ──────────────────────────────
# These automatically override the Windows paths in application.properties.
ENV JDTLS_LAUNCHER_JAR=/opt/jdtls/plugins/org.eclipse.equinox.launcher.jar
ENV JDTLS_CONFIG_DIR=/opt/jdtls/config_active
ENV JDTLS_DATA_DIR=/var/jdtls-workspace
ENV TYPESCRIPT_LSP_COMMAND=typescript-language-server

# ── Maven options ────────────────────────────────────────────────────────────
ENV MAVEN_OPTS="-Xmx1g"

WORKDIR /workspace

