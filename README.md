# 🛠️ Gestión de Partes API

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.12-brightgreen?style=for-the-badge&logo=spring)
![Supabase](https://img.shields.io/badge/Supabase-PostgreSQL-blue?style=for-the-badge&logo=supabase)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-informational?style=for-the-badge&logo=openapiinitiative)

Este proyecto es un backend robusto diseñado para la **Gestión de Partes de Trabajo**, construido sobre **Spring Boot 3.5**. Utiliza una arquitectura orientada a servicios, seguridad basada en tokens JWT y documentación automatizada bajo el estándar OpenAPI.

---

## 📖 Documentación de la API (Swagger)

La API implementa **SpringDoc OpenAPI**, lo que permite tener una documentación viva y siempre actualizada que refleja los cambios en el código al instante.

### 🔗 Enlaces de Interés
* **Swagger UI (Interfaz Visual):** [http://localhost:8081/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

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
* **Docker & Docker Compose** - para la instancia local de Supabase.

### 2. Configuración de Supabase Local (Docker)
1. Navega a la carpeta `supabase/docker/` y ejecuta:
   ```bash
   docker-compose -f docker-compose.yml up -d
   ```
2. Espera a que todos los servicios se inicialicen (2-3 minutos).
3. Accede a la **Interfaz de Supabase** en [http://localhost:8080](http://localhost:8080)
4. Abre el **SQL Editor** en la interfaz de Supabase
5. Copia y pega el contenido del archivo `sql/developer.sql` y ejecuta el script

### 3. Configuración del Entorno
Edita tu archivo `src/main/resources/application.properties`:

```properties
# Configuración de Base de Datos (Supabase Local)
spring.datasource.url=jdbc:postgresql://localhost:8081/postgres
spring.datasource.username=postgres
spring.datasource.password=postgres

# Seguridad JWT (Se requiere una clave de al menos 64 caracteres para HS256)
jwt.secret=tu_clave_secreta_super_segura_y_muy_larga_para_evitar_errores_de_seguridad
```
---

## 🛠️ Especificaciones Técnicas

El proyecto se basa en el ecosistema de **Spring** para garantizar escalabilidad y mantenibilidad.

| Componente | Detalles |
|-----------|----------|
| **Framework Principal** | Spring Boot 3.5.12 (Java 17) |
| **Persistencia** | Spring Data JPA con Hibernate |
| **Base de Datos** | Supabase Local (PostgreSQL vía Docker) |
| **Seguridad** | Spring Security + JWT |
| **Validación** | Bean Validation (JSR-380) |
| **Documentación** | SpringDoc OpenAPI (Swagger) |
| **Gestión de Dependencias** | Maven |

---

## 📂 Estructura del Proyecto

El proyecto sigue una **Arquitectura por Capas** para separar responsabilidades:

```
src/main/java/com/example/gestion_partes/
├── 📁 config/           # Configuraciones globales (Seguridad, Swagger, CORS)
├── 📁 controller/       # Capa de entrada (REST Controllers)
├── 📁 dto/              # Objetos de transferencia de datos
├── 📁 model/            # Modelos de datos (Entidades JPA)
├── 📁 repo/             # Repositorios (Acceso a datos)
├── 📁 service/          # Capa de negocio (Lógica y casos de uso)
└── 📁 resources/        # Configuración de la aplicación
```

---

## 🚀 Guía Rápida de Inicio

### 1️⃣ Clonar el Repositorio

```bash
git clone https://github.com/Riu22/gestion_partes.git
cd gestion_partes
```

### 2️⃣ Compilar y Ejecutar

```bash
# Instalar dependencias
mvn clean install

# Ejecutar la aplicación
mvn spring-boot:run
```

La API estará disponible en: **http://localhost:8081**

---

## 🗄️ Scripts SQL Disponibles

El proyecto incluye dos scripts SQL en la carpeta `sql/`:

| Script | Propósito | Uso |
|--------|-----------|-----|
| **developer.sql** | Esquema completo para desarrollo con datos de prueba | Ejecutar en Supabase durante setup inicial |
| **prod.sql** | Esquema optimizado para producción | Usar en ambiente de producción |

### Ejecutar Scripts en Supabase Local

1. Accede a la interfaz de Supabase en [http://localhost:8080](http://localhost:8080)
2. Ve a **SQL Editor** en el menú lateral
3. Haz clic en **New Query**
4. Copia y pega el contenido del archivo `sql/developer.sql`
5. Ejecuta la query (botón Play o Ctrl+Enter)

