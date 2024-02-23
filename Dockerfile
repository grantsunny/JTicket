FROM maven:3.9.6-eclipse-temurin-17 AS service-build
WORKDIR /
ADD . .
RUN mvn clean package

FROM eclipse-temurin:17-jre-jammy
LABEL authors="Grant Yang"
LABEL description="Stoneticket Backend Image"

ENV JAVA_OPTS="-XX:MinRAMPercentage=50 -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+UseContainerSupport"
WORKDIR /
COPY --from=service-build /target/*.jar service.jar
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
ENTRYPOINT java $JAVA_OPTS -jar /service.jar