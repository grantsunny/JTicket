FROM maven:3.10.0-eclipse-temurin-27 AS service-build
ARG MAVEN_ARGS=""
WORKDIR /workspace
COPY . .
RUN mkdir -p .m2/repository .m2/tmp && \
    MAVEN_OPTS="-Djava.io.tmpdir=$PWD/.m2/tmp -Djansi.tmpdir=$PWD/.m2/tmp" \
    mvn ${MAVEN_ARGS} -DskipTests -DskipJavaScriptTests=true -Dmaven.repo.local=.m2/repository package

FROM eclipse-temurin:27-jre-resolute
LABEL authors="Grant Yang"
LABEL description="JTicket Backend Image"
ENV JAVA_OPTS="-XX:MinRAMPercentage=50 \
-XX:InitialRAMPercentage=50 \
-XX:MaxRAMPercentage=75 \
-XX:+UseG1GC \
-XX:+UseContainerSupport "
WORKDIR /
COPY --from=service-build /workspace/target/*.jar service.jar
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /service.jar"]
