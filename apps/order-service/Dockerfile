FROM gradle:9.0.0-jdk21 AS build

WORKDIR /workspace

COPY settings.gradle build.gradle ./

COPY apps/api-gateway/build.gradle apps/api-gateway/build.gradle
COPY apps/member-service/build.gradle apps/member-service/build.gradle
COPY apps/product-service/build.gradle apps/product-service/build.gradle
COPY apps/order-service/build.gradle apps/order-service/build.gradle

COPY apps/order-service/src apps/order-service/src

RUN gradle :apps:order-service:bootJar --no-daemon


FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 appuser

WORKDIR /app

COPY --from=build \
    /workspace/apps/order-service/build/libs/*.jar \
    app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]