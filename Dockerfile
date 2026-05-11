# Etapa 1: Compilación (JDK 21 sobre Alpine)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos archivos de configuración de Maven
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Damos permisos y descargamos dependencias
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

# Copiamos el código y compilamos
COPY src src
RUN ./mvnw clean package -DskipTests

# Etapa 2: Ejecución (JRE 21 sobre Alpine)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos el jar generado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

# Comando para ejecutar la aplicación
# Nota: Alpine usa 'sh' en lugar de 'bash'.
# Se recomienda usar la forma de lista para mejor manejo de señales de Docker.
ENTRYPOINT ["java", "-jar", "app.jar"]