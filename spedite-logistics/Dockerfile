FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spedite && adduser -S spedite -G spedite
COPY --from=build /app/target/spedite-logistics-0.0.1-SNAPSHOT.jar app.jar

USER spedite
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
