FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre
WORKDIR /app
COPY target/llm-eval-hub-0.1.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
