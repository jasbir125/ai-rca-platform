# Build context is rca-platform/ (not rca-api/) so this can also COPY the sibling
# knowledge/ directory — see infrastructure/docker/docker-compose.yml's rca-api
# service, which sets `context: ../../rca-platform` and `dockerfile: rca-api/Dockerfile`.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY rca-api/target/rca-api-*.jar app.jar
COPY knowledge /knowledge
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
