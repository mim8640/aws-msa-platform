FROM gradle:9.0.0-jdk21 AS build

WORKDIR /workspace

COPY settings.gradle build.gradle ./

COPY apps/api-gateway/build.gradle apps/api-gateway/build.gradle
COPY apps/member-service/build.gradle apps/member-service/build.gradle
COPY apps/product-service/build.gradle apps/product-service/build.gradle
COPY apps/order-service/build.gradle apps/order-service/build.gradle

COPY apps/member-service/src apps/member-service/src

RUN gradle :apps:member-service:bootJar --no-daemon


FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 appuser

WORKDIR /app

COPY --from=build \
    /workspace/apps/member-service/build/libs/member-service-0.1.0.jar \
    /app/app.jar

# RDS CA certificates
COPY apps/member-service/certs/rds-root-*.pem /tmp/rds-certs/

# Import RDS CA certificates into Java truststore
RUN set -eu; \
    for cert in /tmp/rds-certs/*.pem; do \
        keytool -importcert -noprompt -trustcacerts \
          -cacerts \
          -storepass changeit \
          -alias "$(basename "$cert" .pem)" \
          -file "$cert"; \
    done; \
    rm -rf /tmp/rds-certs

USER 10001

EXPOSE 8080

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]