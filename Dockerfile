FROM maven:3.9.16-eclipse-temurin-21 AS service-build
WORKDIR /
COPY . .
RUN mvn clean package

FROM eclipse-temurin:21-jre-noble
LABEL authors="Grant Yang"
LABEL description="JTicket Backend Image"
ENV JAVA_OPTS="-XX:MinRAMPercentage=50 \
-XX:InitialRAMPercentage=50 \
-XX:MaxRAMPercentage=75 \
-XX:+UseG1GC \
-XX:+UseContainerSupport "
WORKDIR /
COPY --from=service-build /target/*.jar service.jar
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
ENTRYPOINT java $JAVA_OPTS -jar /service.jar
