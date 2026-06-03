# Gestión de Partes API

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen?style=for-the-badge&logo=spring)
![Supabase](https://img.shields.io/badge/Supabase-PostgreSQL-blue?style=for-the-badge&logo=supabase)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-informational?style=for-the-badge&logo=openapiinitiative)

Backend REST para la gestión de partes de trabajo de una empresa de construcción/instalaciones.
Permite a operarios, encargados y jefes de obra registrar horas trabajadas por obra, con control
de ausencias, firma digital, generación de informes PDF/Excel y un sistema jerárquico de
visibilidad según el rol.

---

## Documentación de la API (Swagger)

La API implementa **SpringDoc OpenAPI 2.7.0**, con documentación viva y siempre actualizada.

- **Swagger UI:** [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)

### Pruebas con JWT

1. Haz clic en **"Authorize"** (esquina superior derecha).
2. Introduce tu token JWT.
3. Swagger incluirá automáticamente `Authorization: Bearer <token>` en todas las peticiones.

---

## Guía de Instalación y Ejecución

### Requisitos Previos

- **JDK 21** o superior.
- **Maven 3.8+**.
- **Docker & Docker Compose** — para la instancia local de Supabase.

### Configuración de Supabase Local (Docker)

> El directorio `supabase/` contiene un clon del repositorio oficial de Supabase.

1. Navega a `supabase/docker/` y ejecuta:
   ```bash
   docker-compose -f docker-compose.yml up -d
   ```
2. Espera a que todos los servicios se inicialicen (2-3 minutos).
3. Accede a la interfaz de Supabase en [http://localhost:8000](http://localhost:8000).
4. Abre el **SQL Editor**, copia el contenido del script correspondiente y ejecútalo.

### Configuración del Entorno

Edita `src/main/resources/application.properties`:

```properties
# Base de Datos (Supabase Local)
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres.your-tenant-id
spring.datasource.password=your-super-secret-and-long-postgres-password

# JWT (mínimo 32 caracteres, recomendado 64+)
jwt.secret=tu_clave_secreta_super_segura_y_muy_larga_para_evitar_errores_de_seguridad

# Supabase
supabase.url=http://localhost:8000
supabase.public.url=http://localhost:8000
supabase.service.key=eyJ...
```

---

## Especificaciones Técnicas

| Componente | Detalles |
|-----------|----------|
| **Framework Principal** | Spring Boot 3.4.0 (Java 21) |
| **Persistencia** | Spring Data JPA con Hibernate |
| **Base de Datos** | PostgreSQL (Supabase) |
| **Seguridad** | Spring Security + OAuth2 Resource Server + JWT (HMAC-SHA256) |
| **Validación** | Bean Validation (JSR-380) |
| **Documentación** | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| **Gestión de Dependencias** | Maven |
| **Puerto** | 8081 |

---

## Estructura del Proyecto

```
src/main/java/com/example/gestion_partes/
├── config/           # Configuraciones globales (Security, Swagger, CORS, RestTemplate)
├── controller/       # REST Controllers (10 controladores)
├── dto/              # Data Transfer Objects (18 DTOs)
├── helper/           # Utilidades (calendario laboral con festivos)
├── model/            # Entidades JPA y enums (11 clases)
├── repo/             # Repositorios Spring Data JPA (8 repos)
└── service/          # Lógica de negocio (11 servicios)
```

---

## Modelo de Roles y Jerarquía

| Rol | Visibilidad | Permisos especiales |
|-----|------------|-------------------|
| **ADMINISTRACION** | Total (todos los partes, obras, usuarios) | CRUD usuarios, eliminar obras, validar partes de jefe |
| **GESTION** | Total | Crear usuarios, gestionar obras/asignaciones, validar partes |
| **JEFE_DE_OBRA** | Sus obras y subordinados | Partes semanales, contabilidad filtrada, PDFs |
| **ENCARGADO** | Obras asignadas y sus operarios | Partes diarios, búsqueda |
| **OPERARIO** | Solo sus propios partes | Crear partes (máx. 2 semanas atrás) |

---

## Scripts SQL

| Script | Propósito |
|--------|-----------|
| **esquema_completo.sql** | Esquema completo actualizado con todas las tablas, enums, funciones y triggers |
| **developer.sql** | Esquema para desarrollo con datos de prueba (reinicia la BD) |
| **prod.sql** | Esquema optimizado para producción con migraciones |

---

## Docker

```bash
# Construir imagen
docker build -t gestion-partes .

# Ejecutar contenedor
docker run -p 8081:8081 gestion-partes
```

El `Dockerfile` usa compilación en dos etapas (multi-stage build):
1. **Build**: JDK 21 Alpine + Maven Wrapper.
2. **Runtime**: JRE 21 Alpine (~200 MB).

---

## CI/CD (GitHub Actions)

El workflow en `.github/workflows/docker-publish.yml`:
1. Compila con Maven y JDK 21.
2. Construye imagen Docker multi-plataforma (`linux/amd64`, `linux/arm64`).
3. Publica en Docker Hub automáticamente al hacer push a `main`.

---

## Versión y APK

La API expone un endpoint público de versión:

```bash
GET /api/v1/version
# Response: { "version": "1.0.17", "url": "http://..." }
```

Se utiliza para que la app móvil (Android APK) verifique si hay una versión más reciente disponible.

---

## Legal

Este software ha sido desarrollado por Riu (https://github.com/riu22) y su propiedad
intelectual pertenece al autor. La empresa dispone de una licencia
limitada de uso. Cualquier modificación realizada sin el consentimiento
del autor no es responsabilidad del desarrollador original.
