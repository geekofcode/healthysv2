FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S healthys && adduser -S healthys -G healthys
COPY --from=build /workspace/target/*.jar app.jar
USER healthys
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
