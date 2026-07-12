FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/wushi-app-*.jar app.jar
COPY ../scripts /app/scripts
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium wget gnupg \
    && wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y /app/google-chrome-stable_current_amd64.deb || true \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
