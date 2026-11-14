FROM node:26-bookworm-slim AS javascript-runtime

FROM maven:3.10.0-eclipse-temurin-27 AS service-build
COPY --from=javascript-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=javascript-runtime /usr/lib/*-linux-gnu/libatomic.so.1 /usr/local/lib/
RUN ldconfig
WORKDIR /
COPY . .
RUN mvn clean package

FROM eclipse-temurin:27-jre-resolute
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
