# 🛠️ Gestión de Partes API

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.12-brightgreen?style=for-the-badge&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-latest-blue?style=for-the-badge&logo=postgresql)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-informational?style=for-the-badge&logo=openapiinitiative)

Este proyecto es un backend robusto diseñado para la **Gestión de Partes de Trabajo**, construido sobre **Spring Boot 3.5**. Utiliza una arquitectura orientada a servicios, seguridad basada en tokens JWT y documentación automatizada bajo el estándar OpenAPI.

---

## 📖 Documentación de la API (Swagger)

La API implementa **SpringDoc OpenAPI**, lo que permite tener una documentación viva y siempre actualizada que refleja los cambios en el código al instante.

### 🔗 Enlaces de Interés
* **Swagger UI (Interfaz Visual):** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **Especificación OpenAPI (JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
* **Especificación OpenAPI (YAML):** [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

### 🔐 Pruebas con Seguridad (JWT)
Para probar los endpoints protegidos desde la interfaz de Swagger:
1. Haz clic en el botón superior derecho **"Authorize"**.
2. Introduce tu token JWT generado.
3. Swagger incluirá automáticamente la cabecera `Authorization: Bearer <token>` en todas las peticiones que realices desde el navegador.

---

## 🚀 Guía de Instalación y Ejecución

### 1. Requisitos Previos
* **JDK 17** o superior.
* **Maven 3.8+**.
* **PostgreSQL** instalado y configurado.

### 2. Configuración del Entorno
Edita tu archivo `src/main/resources/application.properties` con los parámetros correspondientes:

```properties
# Configuración de Base de Datos
spring.datasource.url=jdbc:postgresql://localhost:5432/gestion_partes_db
spring.datasource.username=tu_usuario
spring.datasource.password=tu_contraseña

# Seguridad JWT (Se requiere una clave de al menos 64 caracteres para HS256)
jwt.secret=tu_clave_secreta_super_segura_y_muy_larga_para_evitar_errores_de_seguridad
```
---
###🛠️ Especificaciones Técnicas
Este proyecto se basa en el ecosistema de Spring para garantizar escalabilidad y mantenibilidad.

Framework Principal: Spring Boot 3.2.x (Java 17).

Persistencia: Spring Data JPA con Hibernate como ORM.

Seguridad: Spring Security con autenticación basada en JWT (JSON Web Tokens).

Validación: Bean Validation (JSR-380) para asegurar la integridad de los datos de entrada.

Documentación: SpringDoc OpenAPI para la generación automática de Swagger.

Gestión de Dependencias: Maven.

📂 Estructura del Proyecto
El proyecto sigue una arquitectura Layered Architecture (Arquitectura por Capas) para separar responsabilidades:

Plaintext
src/main/java/com/tuempresa/gestionpartes/
├── 📁 config          # Configuraciones globales (Seguridad, Swagger, CORS)
├── 📁 controllers     # Capa de entrada (REST Controllers)
├── 📁 dtos            # Objetos de transferencia de datos (Request/Response)
├── 📁 entities        # Modelos de datos (Mapeo JPA/PostgreSQL)
├── 📁 exceptions      # Manejo global de errores y excepciones personalizadas
├── 📁 repositories    # Interfaz de comunicación con la base de datos
├── 📁 security        # Lógica de JWT, Filtros y detalles de usuario
└── 📁 services        # Capa de negocio (Lógica y casos de uso)
    └── 📁 impl        # Implementaciones de las interfaces de servicio
🚀 Guía de Instalación y Ejecución
1. Clonar el repositorio
Bash
git clone https://github.com/tu-usuario/gestion-partes-api.git
cd gestion-partes-api
2. Configurar la Base de Datos
Asegúrate de tener PostgreSQL corriendo. Crea una base de datos llamada gestion_partes_db:

SQL
CREATE DATABASE gestion_partes_db;
3. Configurar variables de entorno
Edita el archivo src/main/resources/application.properties (o usa variables de entorno) con tus credenciales:

Properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gestion_partes_db
spring.datasource.username=tu_usuario
spring.datasource.password=tu_contraseña
jwt.secret=${JWT_SECRET:una_clave_muy_larga_de_al_menos_64_caracteres_para_seguridad}
4. Compilar y Ejecutar
Puedes lanzar la aplicación usando Maven desde la raíz del proyecto:

Bash
# Limpiar e instalar dependencias
mvn clean install

# Ejecutar la aplicación
mvn spring-boot:run
La API estará disponible en http://localhost:8080.
