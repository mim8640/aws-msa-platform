FROM gradle:9.0.0-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./

COPY apps/api-gateway/build.gradle apps/api-gateway/build.gradle
COPY apps/member-service/build.gradle apps/member-service/build.gradle
COPY apps/product-service/build.gradle apps/product-service/build.gradle
COPY apps/order-service/build.gradle apps/order-service/build.gradle

COPY apps/api-gateway/src apps/api-gateway/src

RUN gradle :apps:api-gateway:bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 appuser
WORKDIR /app

COPY --from=build /workspace/apps/api-gateway/build/libs/api-gateway-0.1.0.jar /app/app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]