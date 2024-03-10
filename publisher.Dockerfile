FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY ./publisher/target/*.jar ./
CMD ["java", "-jar", "CyclopPublisher-0.0.1.jar"]
