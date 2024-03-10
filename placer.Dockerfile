FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY ./order-placer/target/*.jar ./
CMD ["java", "-jar", "CyclopOrderPlacer-0.0.1-SNAPSHOT.jar"]
