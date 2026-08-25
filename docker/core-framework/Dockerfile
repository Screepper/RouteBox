FROM openjdk:24-ea-jdk-slim-bookworm

WORKDIR /app

COPY . /app/

RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*

# create directory for logs and data
RUN mkdir -p /data/RouteBox/logs /data/RouteBox/ && \
  chmod -R 777 /data/RouteBox/

EXPOSE 12100

CMD ["java", "-jar", "core-framework.jar"]