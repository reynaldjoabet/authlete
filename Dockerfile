# Container image for the Authlete authorization server.
#
# Two stages: sbt-assembly produces a single self-contained jar, and the runtime stage carries that
# jar and a JRE. Nothing from the build tree -- sources, sbt, the dependency cache -- reaches the
# shipped image.

# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------
# The sbt in this tag is only the launcher; it reads project/build.properties and fetches the 2.0.8
# the project pins. The JDK version is what matters, and it must be at least the
# -java-output-version in scalacOptions (17).
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.12_8_2.x AS build

WORKDIR /build

# Dependency resolution in its own layer. These files change far less often than sources, so editing
# src/ reuses this layer instead of re-resolving ~150 artifacts.
COPY project/build.properties project/plugins.sbt project/Dependencies.scala project/
COPY build.sbt ./
RUN sbt update

# The generated Authlete client is produced from this spec by a sourceGenerator at compile time, so
# the module has to be present before anything is built.
COPY modules/ modules/
COPY src/ src/

# Writes /build/target/authlete.jar -- assemblyOutputPath pins it to the repository root rather than
# sbt 2's content-addressed target/out tree, so the COPY below needs no Scala version in its path.
RUN sbt assembly

# ---------------------------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------------------------
# JRE, not JDK: nothing compiles at runtime, and the smaller image carries less to patch.
FROM eclipse-temurin:21-jre-jammy AS runtime

# Unprivileged, no home directory, no login shell. The process reads its jar and binds a port; it
# writes nothing to disk.
RUN groupadd --system --gid 1001 authlete \
 && useradd --system --uid 1001 --gid authlete --no-create-home --shell /usr/sbin/nologin authlete

WORKDIR /app

# Owned by root and read-only to the running user: the process has no reason to modify its own code,
# and this way a compromise of it cannot rewrite the jar.
COPY --from=build --chown=root:root /build/target/authlete.jar /app/authlete.jar

USER authlete:authlete

EXPOSE 8080

# Every setting comes from the environment; .env.example lists them all and marks which are
# required. Nothing else is baked in, so one image serves every deployment.
ENV SERVER_HOST=0.0.0.0 \
    SERVER_PORT=8080

# Exec form, so the JVM is PID 1 and receives SIGTERM directly. Under the shell form a shell would
# be PID 1 and would not forward the signal, so the graceful drain in Main -- which is what makes
# SERVER_SHUTDOWN_TIMEOUT mean anything -- would never run, and every deployment would end by
# cutting off in-flight requests.
#
# MaxRAMPercentage rather than a fixed -Xmx: the heap then tracks the container's memory limit
# instead of a number that has to be edited in step with it. Container awareness itself needs no
# flag, having been on by default since JDK 10.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "/app/authlete.jar"]

# No HEALTHCHECK on purpose. This image ships no HTTP client to write one with, and adding curl
# would widen the runtime surface to serve a mechanism Kubernetes and ECS both ignore in favour of
# their own probes. Point those at:
#
#   GET /health/live   liveness  -- process-local, touches no dependency
#   GET /health/ready  readiness -- fails while the JWKS cache is cold or stale
#
# The distinction matters: a liveness probe that checked dependencies would restart every replica
# during an upstream outage.
