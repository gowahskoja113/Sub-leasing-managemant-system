# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render injects PORT; Spring reads server.port
ENV PORT=8080
ENV TZ=Asia/Ho_Chi_Minh
EXPOSE 8080

# -Xmx1g: heap cố định trên VPS 4GB; ONNX native memory nằm ngoài heap — đừng tăng quá
ENTRYPOINT ["sh", "-c", "java -Xmx1g -XX:MaxMetaspaceSize=256m -Duser.timezone=Asia/Ho_Chi_Minh -Dserver.port=${PORT} -jar app.jar"]
