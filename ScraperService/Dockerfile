# syntax=docker/dockerfile:1
# Multi-stage build for a Jobber Spring Boot service (Java 25 / Spring Boot 4.1).
# Stage 1 compiles the executable jar with the project's Maven wrapper; stage 2 runs it on a JRE.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Copy the wrapper + pom first: this layer is cached unless the build config changes,
# so editing sources doesn't force a full dependency re-resolve.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
# BuildKit cache mount keeps the local Maven repo warm across image builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests clean package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
# DocumentDB requires TLS. Import Amazon's global RDS/DocDB CA bundle into the JVM truststore so the
# MongoDB driver trusts DocumentDB's server certificate with real validation (no insecure flags).
# The bundle holds many regional CA certs, so split it and import each (keytool imports only the first
# cert from a multi-cert PEM otherwise).
ADD https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /tmp/rds-bundle.pem
RUN cd /tmp \
 && csplit -z -s -f rds- -b '%03d.pem' rds-bundle.pem '/-----BEGIN CERTIFICATE-----/' '{*}' \
 && for f in /tmp/rds-*.pem; do \
      keytool -importcert -noprompt -alias "docdb-$(basename "$f" .pem)" -file "$f" -cacerts -storepass changeit; \
    done \
 && rm -f /tmp/rds-*.pem /tmp/rds-bundle.pem
COPY --from=build /app/target/*.jar app.jar
# Container-internal port comes from each service's SERVER_PORT default (set in compose).
ENTRYPOINT ["java", "-jar", "app.jar"]
