# --- ETAPA 1: Construcción (Build) ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiamos solo los archivos de configuración de dependencias primero
# Esto aprovecha el sistema de capas de Docker (caché)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw
# Descargamos las dependencias (se queda en caché si el pom no cambia)
RUN ./mvnw dependency:go-offline

# Copiamos el código fuente y compilamos
COPY src src
RUN ./mvnw clean package -DskipTests

# --- ETAPA 2: Ejecución (Runtime) ---
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Creamos un usuario sin privilegios por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos solo el JAR resultante de la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Variables de entorno para optimizar la RAM en planes gratuitos
ENV JAVA_OPTS="-Xmx400m -Xms256m -XX:+UseSerialGC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]