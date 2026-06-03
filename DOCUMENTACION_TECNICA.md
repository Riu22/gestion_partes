# Documentación Técnica — Gestión de Partes API

## 1. Resumen del Proyecto

Backend REST para la gestión de partes de trabajo de una empresa de construcción/instalaciones. Permite a operarios, encargados y jefes de obra registrar sus horas trabajadas por obra, con control de ausencias, firma digital, generación de informes PDF/Excel y un sistema jerárquico de visibilidad según el rol.

| Atributo | Valor |
|---|---|
| **Framework** | Spring Boot 3.4.0 |
| **Java** | 21 |
| **Gestor de dependencias** | Maven |
| **Base de datos** | PostgreSQL (Supabase) |
| **Seguridad** | Spring Security + OAuth2 Resource Server + JWT (HMAC-SHA256) |
| **Documentación API** | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| **Puerto** | 8081 |

---

## 2. Arquitectura General

### 2.1 Capas

```
controller → service → repo → (JPA/PostgreSQL)
                  ↕
               helper / dto
```

### 2.2 Estructura de paquetes

```
com.example.gestion_partes/
├── config/           → Configuración global (Security, OpenAPI, RestTemplate)
├── controller/       → REST controllers (endpoints)
├── dto/              → Data Transfer Objects (records e interfaces)
├── helper/           → Clases de utilidad (calendario laboral)
├── model/            → Entidades JPA y enums
├── repo/             → Repositorios Spring Data JPA
└── service/          → Lógica de negocio
```

### 2.3 Flujo de autenticación

1. El frontend (app Flutter) se autentica contra **Supabase Auth**.
2. Supabase devuelve un **JWT firmado con HMAC-SHA256**.
3. Cada petición al backend incluye `Authorization: Bearer <JWT>`.
4. El backend valida el JWT localmente con `JwtDecoder` + `SecretKeySpec`.
5. El `CustomJwtConverter` extrae el rol de `app_metadata` o `user_metadata` y lo mapea a `ROLE_*`.

---

## 3. Modelo de Datos (Base de datos PostgreSQL)

### 3.1 Esquema relacional

```
auth.users (gestión externa por Supabase)
    └── perfiles (1:1, FK → auth.users.id)
            ├── jefe_directo_id → perfiles.id (autorreferencia)
            ├── postventa (boolean)
            ├── grupo_profesional (text)
            └── obras (N:M a través de asignaciones_obra)

asignaciones_obra (relación perfil ↔ obra)
    ├── perfil_id → perfiles.id
    └── obra_id → obras.id

partes_trabajo (partes de operarios/encargados)
    ├── usuario_id → perfiles.id
    ├── point_obra_id → obras.id
    ├── trabajos_extra (text, nullable)
    ├── creado_por_gestor (boolean)
    └── firma_url (text)

partes_jefe (partes de jefes de obra, con fechas de período)
    ├── usuario_id → perfiles.id
    └── ── partes_jefe_obras (detalle por obra)
            ├── parte_jefe_id → partes_jefe.id
            └── obra_id → obras.id

ausencias (bajas/vacaciones/paternidad por perfil)
    ├── perfil_id → perfiles.id
    └── obra_id → obras.id (nullable, opcional)

fechas_permitidas (edición retroactiva)
    └── perfil_id → perfiles.id
```

### 3.2 Enumeraciones

| Enum | Valores |
|---|---|
| `user_rol` | `ADMINISTRACION`, `GESTION`, `OPERARIO`, `JEFE_DE_OBRA`, `ENCARGADO` |
| `especialidad` | `ELECTRICIDAD`, `FONTANERIA` |
| `AusenciaTipo` | `BAJA`, `VACACIONES`, `PATERNIDAD` |

### 3.3 Descripción de tablas principales

| Tabla | Propósito |
|---|---|---|
| `perfiles` | Usuarios del sistema, vinculados a `auth.users` de Supabase; incluye `postventa` y `grupo_profesional` |
| `obras` | Obras/instalaciones con ubicación y código |
| `asignaciones_obra` | Asignación de encargados/jefes a obras |
| `partes_trabajo` | Registro diario de horas de OPERARIO/ENCARGADO por obra; incluye `trabajos_extra`, `firma_url` y `creado_por_gestor` |
| `partes_jefe` | Parte semanal/quincenal de JEFE_DE_OBRA con fechas inicio/fin |
| `partes_jefe_obras` | Detalle de horas eléctricas/mecánicas por obra en partes de jefe |
| `ausencias` | Ausencias laborales (baja, vacaciones, paternidad); `obra_id` opcional para imputar vacaciones a una obra |
| `fechas_permitidas` | Fechas habilitadas para edición/eliminación retroactiva |

---

## 4. API REST (Endpoints)

### 4.1 Autenticación y perfil (`/api/v1/user`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/me` | Cualquiera autenticado | Perfil del usuario actual |
| POST | `/create_user` | ADMIN, GESTION | Crear usuario en Supabase Auth + BD |
| PUT | `/update_user/{id}` | ADMIN, GESTION | Actualizar perfil |
| DELETE | `/delete_user/{id}` | ADMIN | Eliminar usuario |
| GET | `/all` | ADMIN, GESTION | Listar todos los perfiles |

### 4.2 Partes de trabajo (`/api/v1/partes`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/new_parte` | ADMIN, GESTION, OPERARIO, ENCARGADO | Crear parte con firma opcional |
| GET | `/get_partes` | Cualquiera autenticado | Listar partes visibles jerárquicamente |
| PUT | `/update/{parteId}` | Cualquiera autenticado | Editar parte (con control de fecha) |
| DELETE | `/delete/{parteId}` | Cualquiera autenticado | Eliminar parte (con control de fecha) |
| GET | `/buscar` | ADMIN, GESTION, JEFE, ENCARGADO | Búsqueda filtrada por obra/operario/especialidad |
| GET | `/fechas-con-parte/{id}` | ADMIN, GESTION | Fechas con parte de un usuario |
| GET | `/mis-fechas-con-parte` | Cualquiera autenticado | Fechas con parte del usuario actual |
| GET | `/puede-fecha-libre` | Cualquiera autenticado | Verificar si una fecha está habilitada |
| GET | `/{id}` | Público | Obtener un parte de trabajo por su ID |

### 4.3 Partes de jefe de obra (`/api/v1/partes`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/new_parte_jefe` | JEFE_DE_OBRA | Crear parte semanal con distribución por obra |
| PUT | `/update_parte_jefe/{parteId}` | JEFE, ADMIN, GESTION | Actualizar parte de jefe |
| GET | `/get_partes_jefe` | ADMIN, GESTION, JEFE | Listar partes de jefe |
| PUT | `/validar_jefe/{parteId}` | ADMIN, GESTION | Validar parte de jefe |
| DELETE | `/delete_jefe/{parteId}` | ADMIN, JEFE | Eliminar parte de jefe |
| GET | `/informe_jefe/{parteId}` | ADMIN, GESTION, JEFE | Informe detallado de un parte |
| GET | `/resumen-mensual-jefe` | ADMIN, GESTION, JEFE | Resumen mensual con totales por obra |
| GET | `/informe-jefe-rango` | ADMIN, GESTION, JEFE | Informe por rango de fechas |
| GET | `/resumen-mensual-por-usuario` | ADMIN, GESTION | Resumen mensual agregado por usuario |

### 4.4 Obras (`/api/v1/obra`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/` | Todos | Listar todas las obras |
| GET | `/activas` | Todos | Listar solo obras activas |
| POST | `` | ADMIN, GESTION | Crear obra |
| PUT | `/update_obra/{id}` | ADMIN, GESTION | Actualizar obra |
| DELETE | `/delete/{id}` | ADMIN | Eliminar obra |

### 4.5 Asignaciones (`/api/v1/asignaciones`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/asignar_a_obra/{perfilId}/{obraId}` | ADMIN, GESTION | Asignar encargado/jefe a obra |
| PUT | `/asignar_subordinado/{subordinadoId}/{jefeId}` | ADMIN, GESTION | Asignar subordinado (operario/encargado) a un jefe |
| PUT | `/asignar_encargado/{encargadoId}/{jefeId}` | ADMIN, GESTION | Asignar encargado a jefe de obra |
| PUT | `/asignar_obras_batch/{perfilId}` | ADMIN, GESTION | Asignación masiva de obras a un perfil |
| PUT | `/asignar_subordinados_batch/{jefeId}` | ADMIN, GESTION | Asignación masiva de subordinados a un jefe |
| GET | `/obra/{obraId}` | ADMIN, GESTION, JEFE, ENCARGADO | Ver asignaciones de una obra |
| GET | `/perfil/{perfilId}` | ADMIN, GESTION, JEFE, ENCARGADO | Ver obras de un perfil |
| DELETE | `/eliminar/{asignacionId}` | ADMIN, GESTION | Eliminar asignación de obra |
| GET | `/{jefeId}/subordinados` | ADMIN, GESTION | Ver subordinados de un jefe |
| DELETE | `/quitar_subordinado/{usuarioId}` | ADMIN, GESTION | Quitar jefe directo |
| GET | `/mis_obras` | Cualquiera autenticado | Obras del usuario actual |

### 4.6 Contabilidad y quincenas (`/api/v1/quincena`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `` | ADMIN, GESTION | Resumen de quincena |
| GET | `/exportar` | ADMIN, GESTION | Exportar quincena a XLSX |
| GET | `/contabilidad-detalle-json` | ADMIN, GESTION | Detalle de contabilidad (JSON) |
| GET | `/exportar-detalle-csv` | ADMIN, GESTION | Exportar detalle a XLSX |
| GET | `/jefe/contabilidad-detalle-json` | JEFE_DE_OBRA | Detalle filtrado por obras asignadas |
| GET | `/jefe/exportar-detalle-csv` | JEFE_DE_OBRA | Exportar detalle filtrado |

### 4.7 PDF y ZIP (`/api/v1/pdf`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/partes` | ADMIN, GESTION, JEFE_DE_OBRA | PDF agrupado por obra+especialidad |
| GET | `/partes-zip` | ADMIN, GESTION, JEFE_DE_OBRA | ZIP con un PDF por obra+especialidad |
| GET | `/zip-por-operario` | ADMIN, GESTION, JEFE_DE_OBRA | ZIP con un PDF por operario+especialidad |

### 4.8 Ausencias (`/api/v1/ausencias`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/dias-sin-parte` | ADMIN, GESTION | Incidencias: días sin parte o incompletos |
| POST | `/laborales` | ADMIN, GESTION | Crear ausencia (baja/vacaciones/paternidad) |
| DELETE | `/laborales/{id}` | ADMIN, GESTION | Eliminar ausencia |
| GET | `/laborales/perfil/{perfilId}` | ADMIN, GESTION | Ausencias de un perfil |

### 4.9 Configuración de fechas libres (`/api/v1/config/fecha-libre`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/habilitar/{id}` | ADMIN, GESTION | Habilitar fechas para edición retroactiva |
| DELETE | `/deshabilitar/{id}/{fecha}` | ADMIN, GESTION | Deshabilitar una fecha |
| DELETE | `/deshabilitar/{id}` | ADMIN, GESTION | Deshabilitar todas las fechas |
| GET | `` | ADMIN, GESTION | Listar todas las fechas habilitadas |
| GET | `/mis-fechas` | Cualquiera autenticado | Fechas habilitadas del usuario |

### 4.10 Versión (`/api/v1/version`)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/` | Público | Versión actual y URL de la APK |

---

## 5. Seguridad

### 5.1 Modelo de roles y jerarquía

```
ADMINISTRACIÓN
    ├── Visibilidad total (todos los partes, obras, usuarios)
    ├── CRUD usuarios
    ├── Eliminar obras y usuarios
    ├── Crear partes para cualquier operario
    └── Validar partes de jefe de obra

GESTIÓN
    ├── Visibilidad total (todos los partes, obras, usuarios)
    ├── CRUD usuarios (excepto eliminar)
    ├── Gestionar obras y asignaciones
    ├── Crear partes para cualquier operario
    └── Validar partes de jefe de obra

JEFE_DE_OBRA
    ├── Crea partes semanales con porcentajes por obra
    ├── Ve partes de sus obras asignadas
    ├── Acceso a contabilidad filtrada por sus obras
    └── Acceso a PDFs de sus obras

ENCARGADO
    ├── Crea partes diarios
    ├── Ve partes de sus operarios subordinados
    └── Restricción: solo obras donde está asignado

OPERARIO
    ├── Crea partes diarios (solo para sí mismo)
    ├── Fecha máxima: 2 semanas atrás
    └── Solo ve sus propios partes
```

### 5.2 Restricciones en partes de operario

- **Límite de 2 semanas**: No se pueden crear partes con fecha anterior a `hoy - 14 días` (excepto ADMIN/GESTION).
- **Autoria**: Un operario solo puede crear partes para sí mismo (ADMIN/GESTION pueden crear para otros).
- **Ausencias**: No se puede crear un parte si el operario está de baja o vacaciones esa fecha.
- **Fechas libres**: Para editar/eliminar un parte de un día que no sea hoy, debe estar habilitado como "fecha libre".
- **Obra activa**: No se puede crear un parte en una obra desactivada.

### 5.3 JWT

- **Algoritmo**: HMAC-SHA256
- **Secreto** configurable vía `jwt.secret` (mínimo 32 caracteres, recomendado 64+).
- **Validación**: Solo `JwtTimestampValidator` (no se valida issuer, compatibilidad con Supabase local).
- **Extracción de rol**: `CustomJwtConverter` busca en `app_metadata.rol` → `user_metadata.rol`.

---

## 6. Servicios principales

| Servicio | Responsabilidad |
|---|---|
| `partes_service` | CRUD de partes de operario/encargado, subida de firmas a Supabase Storage |
| `parte_jefe_service` | CRUD partes de jefe de obra, informes, resúmenes mensuales, validación |
| `obra_service` | CRUD de obras, consulta de obras asignadas |
| `asignacion_service` | Asignación de personal a obras, jerarquía de subordinación |
| `user_service` | Creación/actualización/eliminación de usuarios vía Supabase Admin API |
| `pdf_service` | Generación de PDFs con OpenPDF, agrupación en ZIPs |
| `csv_export_service` | Exportación a Excel (XLSX) con Apache POI, formato de quincena y detalle |
| `contabilidad_service` | Procesado de datos de contabilidad, agregación por obra y persona |
| `ausencias_service` | Gestión de ausencias, detección de días sin parte e incompletos |
| `configuration_service` | Gestión de fechas permitidas para edición retroactiva |
| `calendario_laboral_helper` | Cálculo de horas laborables usando Jollyday (festivos España + Baleares) |
| `FechasPermitidasCleanupJob` | Tarea programada diaria (00:05) que limpia fechas permitidas caducadas con partes ya completados |

---

## 7. Generación de documentos

### 7.1 PDF (OpenPDF)

- PDF por combinación obra+especialidad con tabla de operarios, fechas y horas.
- ZIP con múltiples PDFs (una opción agrupa por obra, otra por operario).
- Incluye cabecera con nombre de obra/operario, pie de página con numeración.
- Diseño con colores corporativos.

### 7.2 Excel (Apache POI - XLSX)

**Quincena**: Resumen agrupado por obra con código, apellidos, nombre, horas y totales.

**Detalle**: Tabla día a día con:
- Columnas fijas: Código, Operario, Categoría, Obra.
- Una columna por día del rango (L/M/X/J/V/S/D).
- Letra "B" (verde) para baja, "V" (rosa) para vacaciones, "P" (azul) para paternidad.
- Totales por persona y subtotales por obra.
- Fines de semana y festivos marcados en rojo.

---

## 8. Configuración del entorno

| Variable / Propiedad | Descripción |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `spring.datasource.url` | URL de conexión PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` / `spring.datasource.username` | Usuario BD |
| `SPRING_DATASOURCE_PASSWORD` / `spring.datasource.password` | Contraseña BD |
| `JWT_SECRET` / `jwt.secret` | Clave secreta HMAC-SHA256 para JWT (mín. 32 caracteres) |
| `SUPABASE_URL` / `supabase.url` | URL interna de la API de Supabase |
| `SUPABASE_PUBLIC_URL` / `supabase.public.url` | URL pública de Supabase |
| `SUPABASE_SERVICE_KEY` / `supabase.service.key` | Service role key de Supabase |
| `SERVER_PORT` / `server.port` | Puerto del servidor (default: 8081) |
| `SHOW_SQL` / `spring.jpa.show-sql` | Mostrar SQL en logs |
| `HIKARI_MAX_POOL` / `spring.datasource.hikari.maximum-pool-size` | Tamaño máximo del pool HikariCP (default: 30) |
| `app.version.actual` | Versión actual de la aplicación (1.0.17) |
| `app.apk.url` | URL de descarga del APK de la app móvil |
| `server.compression.enabled` | Compresión GZip de respuestas (activada) |
| `spring.cache.type` | Tipo de caché (simple) |

---

## 9. Despliegue

### Docker

```bash
docker build -t gestion-partes .
docker run -p 8081:8081 gestion-partes
```

El `Dockerfile` usa compilación en dos etapas (multi-stage build):
1. **Build**: JDK 21 Alpine + Maven Wrapper para compilar.
2. **Runtime**: JRE 21 Alpine, imagen ligera (~200 MB).

### CI/CD (GitHub Actions)

El workflow en `.github/workflows/docker-publish.yml`:
1. Compila con Maven y JDK 21.
2. Construye imagen Docker multi-plataforma (`linux/amd64`, `linux/arm64`).
3. Sube a Docker Hub en `pushes a main`.

---

## 10. Dependencias externas

| Dependencia | Versión | Uso |
|---|---|---|
| `spring-boot-starter-web` | 3.4.0 | REST API |
| `spring-boot-starter-data-jpa` | 3.4.0 | Persistencia JPA/Hibernate |
| `spring-boot-starter-security` | 3.4.0 | Seguridad |
| `spring-boot-starter-oauth2-resource-server` | 3.4.0 | Validación JWT OAuth2 |
| `spring-boot-starter-validation` | 3.4.0 | Validación de DTOs |
| `springdoc-openapi-starter-webmvc-ui` | 2.7.0 | Swagger UI + OpenAPI |
| `postgresql` | - | Driver PostgreSQL |
| `jollyday-core` + `jollyday-jaxb` | 2.6.0 | Cálculo de festivos (España + Baleares) |
| `openpdf` | 1.3.30 | Generación de PDFs |
| `poi-ooxml` | 5.3.0 | Generación de Excel (XLSX) |

---

## 11. Supabase Storage (Firmas)

- **Bucket**: `firmas-partes`
- **Ruta**: `{obra}/{obra}_{operario}_id{parteId}_{dd-MM-yyyy}.{ext}`
- **Formato**: PNG o JPG (se acepta base64 con o sin cabecera `data:image/...`)
- **Autenticación**: Se sube usando `service_role` key de Supabase
- La URL pública se almacena en `partes_trabajo.firma_url`

---

## 12. Endpoint de prueba

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| GET | `/api/v1/prueba/hola` | Público | Endpoint de prueba para verificar que la API responde |

---

## 13. Legal

Este software ha sido desarrollado por Riu (https://github.com/riu22) y su propiedad
intelectual pertenece al autor. La empresa dispone de una licencia
limitada de uso. Cualquier modificación realizada sin el consentimiento
del autor no es responsabilidad del desarrollador original.

---

## 14. Notas técnicas adicionales

- **DDL automático**: `spring.jpa.hibernate.ddl-auto=none` — los esquemas se gestionan manualmente vía scripts SQL.
- **Naming strategy**: `spring.jackson.property-naming-strategy=SNAKE_CASE` — la API serializa en snake_case.
- **CORS**: Permitido para todos los orígenes (`setAllowedOriginPatterns("*")`), sin credenciales.
- **RestTemplate**: Bean configurado para llamadas a la API Admin de Supabase.
- **Validaciones**: Las fechas de partes usan `@PastOrPresent`, los porcentajes `@Min(1) @Max(100)`.

## Legal

Este software ha sido desarrollado por Riu (https://github.com/riu22) y su propiedad
intelectual pertenece al autor. La empresa dispone de una licencia
limitada de uso. Cualquier modificación realizada sin el consentimiento
del autor no es responsabilidad del desarrollador original.
