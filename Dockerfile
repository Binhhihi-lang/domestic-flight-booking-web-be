# =====================================================================
# Backend Spring Boot — image chạy độc lập, máy đích KHÔNG cần Java/Maven.
#
#   docker build -t vinafly/backend .
#   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=docker vinafly/backend
#
# Thường dùng qua docker-compose.yml (đã có sẵn postgres + redis).
# =====================================================================

# ---------- Stage 1: build bằng Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy riêng pom.xml trước để layer tải dependency được cache lại:
# sửa code Java không phải tải lại toàn bộ thư viện.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Stage 2: chỉ giữ JRE + file jar ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chạy bằng user thường, không phải root.
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
USER spring:spring

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

# shell form để JAVA_OPTS được expand
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
