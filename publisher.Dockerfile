FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY ./publisher/target/*.jar ./
CMD ["sh", "-c", "sleep 30s && java -jar CyclopPublisher-0.0.1.jar"]
