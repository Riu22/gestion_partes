/*
 * SERVICIO: partes_service (Logica de negocio de partes de trabajo)
 *
 * Es el servicio principal del sistema. Gestiona toda la logica de
 * creacion, modificacion, eliminacion y consulta de partes de trabajo
 * diarios de los operarios.
 *
 * Metodos principales:
 *
 * CRUD:
 * - create_parte:      Crea un nuevo parte de trabajo con validaciones
 * - update_parte:      Modifica un parte existente
 * - delete_parte:      Elimina un parte (con limpieza de firma)
 * - get_partes_jerarquico: Lista partes visibles segun el rol del usuario
 *
 * FIRMA:
 * - firmar_parte:      Anade la firma digital a un parte existente
 * - subirFirmaBase64:  Sube la imagen de la firma a Supabase Storage
 * - borrarFirmaStorage: Elimina la imagen de firma de Supabase
 *
 * CONSULTAS:
 * - getFechasConParte: Fechas en las que un trabajador tiene partes
 *
 * VALIDACIONES:
 * - Limite de 2 semanas para no gestores
 * - Verificacion de obra activa
 * - Verificacion de ausencias del trabajador
 * - Permisos de edicion segun fecha habilitada
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.obra_repo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class partes_service {

    @Autowired private partes_trabajo_repo partes_trabajo_repo;
    @Autowired private perfil_repo perfil_repo;
    @Autowired private obra_repo obra_repo;
    @Autowired private configuration_service configuration_service;
    @Autowired private ausencias_service ausenciasService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.public.url}")
    private String supabasePublicUrl;

    @Value("${supabase.service.key}")
    private String supabaseServiceKey;

    // Nombre del bucket en Supabase Storage donde se guardan las firmas
    private static final String BUCKET_FIRMAS = "firmas-partes";

    // Cliente HTTP reutilizable para llamadas a Supabase (mejor rendimiento)
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ─── CREAR PARTE ──────────────────────────────────────────────────────────

    /*
     * Crea un nuevo parte de trabajo.
     *
     * Recibe:
     * - dto: objeto con los datos del parte (obra, trabajador, fecha, horas, etc.)
     * - sub: UUID del usuario autenticado (extraido del token JWT)
     *
     * Devuelve: el partes_trabajo creado y guardado en BD
     *
     * Validaciones:
     * - Si no es gestor: no puede crear partes de mas de 2 semanas de antiguedad
     * - Si no es gestor: solo puede crear partes para si mismo
     * - Los JEFE_DE_OBRA no pueden usar este endpoint (deben usar el de porcentajes)
     * - La obra debe estar activa
     * - El trabajador no debe estar ausente (baja/vacaciones) en esa fecha
     *
     * Si se incluye una firma en base64, se sube a Supabase Storage y se
     * guarda la URL en el parte.
     */
    @Transactional
    public partes_trabajo create_parte(partes_dto dto, String sub) {
        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        // Restricciones para trabajadores normales (no gestores)
        if (!esGestor) {
            LocalDate limiteMinimo = LocalDate.now().minusWeeks(2);
            if (dto.fecha().isBefore(limiteMinimo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No puedes crear partes con mas de 2 semanas de antiguedad");
            }
            if (!solicitante.getId().equals(dto.id_perfil())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo puedes crear partes para ti mismo");
            }
        }

        if (solicitante.getRol() == user_rol.JEFE_DE_OBRA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los jefes de obra deben usar el endpoint de partes por porcentaje");
        }

        UUID idPerfil = dto.id_perfil();

        obra obra = obra_repo.findById(dto.id_obra())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Obra no encontrada"));
        perfil perfil = perfil_repo.findById(idPerfil)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        // No se permiten partes en obras inactivas
        if (!obra.isActiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear partes en una obra inactiva");
        }

        // No se permiten partes si el trabajador esta de baja o vacaciones
        if (ausenciasService.estaAusenteEnFecha(idPerfil, dto.fecha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede crear un parte: el operario esta de baja o vacaciones ese dia");
        }

        partes_trabajo nuevo = new partes_trabajo();
        nuevo.setObra(obra);
        nuevo.setPerfil(perfil);
        nuevo.setFecha(dto.fecha());
        nuevo.setDescripcion(dto.descripcion());
        nuevo.setHoras_normales(dto.horas_normales() != null ? dto.horas_normales() : 8.0);
        nuevo.setHoras_extra(0.0);
        nuevo.setEspecialidad(dto.especialidad());
        nuevo.setNombre_firmado(dto.nombre_firmado());
        nuevo.setTrabajos_extra(dto.trabajo_extra());

        // Marcar si fue creado por un gestor para otro trabajador
        boolean creadoParaOtro = !solicitante.getId().equals(idPerfil);
        nuevo.setCreado_por_gestor(esGestor && creadoParaOtro);

        // Guardar primero para obtener el ID (necesario para la ruta del archivo de firma)
        partes_trabajo guardado = partes_trabajo_repo.save(nuevo);

        // Si hay firma, subirla a Supabase Storage y actualizar el parte
        if (dto.firma_base64() != null && !dto.firma_base64().isBlank()) {
            String firmaUrl = subirFirmaBase64(dto.firma_base64(), guardado, obra, perfil);
            guardado.setFirma_url(firmaUrl);
            guardado = partes_trabajo_repo.save(guardado);
        }

        return guardado;
    }

    // ─── FIRMAR PARTE A POSTERIORI ────────────────────────────────────────────

    /*
     * Anade la firma digital a un parte que ya existe pero no estaba firmado.
     * Permite firmar partes en un momento posterior a su creacion.
     *
     * Recibe:
     * - idParte: ID del parte a firmar
     * - firmaBase64: imagen de la firma en formato base64
     * - sub: UUID del usuario autenticado
     *
     * Devuelve: el parte actualizado con la URL de la firma
     *
     * Validaciones:
     * - Solo el propietario del parte (o gestor) puede firmar
     * - El parte no debe estar ya firmado
     * - La firma no puede estar vacia
     */
    @Transactional
    public partes_trabajo firmar_parte(Long idParte, String firmaBase64, String sub) {
        partes_trabajo parte = partes_trabajo_repo.findById(idParte)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));

        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        // Seguridad: evitar que cualquiera firme partes ajenos
        if (!esGestor && !parte.getPerfil().getId().equals(solicitante.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes firmar partes de otros usuarios");
        }

        if (parte.getFirma_url() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este parte ya ha sido firmado");
        }

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La firma no puede estar vacia");
        }

        String firmaUrl = subirFirmaBase64(firmaBase64, parte, parte.getObra(), parte.getPerfil());
        parte.setFirma_url(firmaUrl);
        return partes_trabajo_repo.save(parte);
    }

    // ─── LISTAR PARTES ────────────────────────────────────────────────────────

    /*
     * Obtiene los partes de trabajo visibles para el usuario segun su rol:
     * - ADMINISTRACION/GESTION: ven todos los partes de los ultimos 30 dias
     * - Otros roles: ven solo los partes que les corresponden segun su
     *   jerarquia (propios, de sus subordinados, de sus obras asignadas)
     *
     * Recibe: sub (UUID del usuario autenticado)
     * Devuelve: lista de partes de trabajo
     */
    public List<partes_trabajo> get_partes_jerarquico(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        LocalDate desde = LocalDate.now().minusDays(30);

        if (usuario.getRol() == user_rol.ADMINISTRACION || usuario.getRol() == user_rol.GESTION) {
            return partes_trabajo_repo.findByFechaGreaterThanEqualOrderByFechaDesc(desde);
        }

        return partes_trabajo_repo.findPartesVisiblesParaPerfilDesde(usuario.getId(), desde);
    }

    // ─── ELIMINAR PARTE ───────────────────────────────────────────────────────

    /*
     * Elimina un parte de trabajo.
     *
     * Recibe: ID del parte y UUID del usuario autenticado
     *
     * Validaciones:
     * - Gestores (ADMIN/GESTION) pueden eliminar cualquier parte
     * - No gestores solo pueden eliminar sus propios partes
     * - No gestores solo pueden eliminar partes de hoy o de fechas habilitadas
     *
     * Antes de eliminar el registro, borra la imagen de firma de Supabase Storage
     * si existe.
     */
    @Transactional
    public void delete_parte(Long parteId, String sub) {
        partes_trabajo parte = partes_trabajo_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));

        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        if (!esGestor) {
            if (!parte.getPerfil().getId().equals(solicitante.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No puedes eliminar partes de otros usuarios");
            }
            boolean esHoy = parte.getFecha().isEqual(LocalDate.now());
            boolean esFechaLibre = configuration_service.fechaPermitida(sub, parte.getFecha());

            if (!esHoy && !esFechaLibre) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo puedes eliminar partes de dias habilitados");
            }
        }

        // Borrar la firma de Supabase Storage antes de eliminar el parte
        if (parte.getFirma_url() != null) {
            borrarFirmaStorage(parte.getFirma_url());
        }

        partes_trabajo_repo.deleteById(parteId);
    }

    // ─── ACTUALIZAR PARTE ─────────────────────────────────────────────────────

    /*
     * Modifica un parte de trabajo existente.
     *
     * Recibe: ID del parte, nuevos datos (partes_dto) y UUID del usuario
     * Devuelve: el parte actualizado
     *
     * Solo se actualizan los campos que vienen informados en el DTO.
     * Validaciones similares a create_parte pero aplicadas a la modificacion.
     * Si se cambia la fecha, verifica que el trabajador no este ausente.
     * Si se actualiza la firma, borra la anterior y sube la nueva.
     */
    @Transactional
    public partes_trabajo update_parte(Long parteId, partes_dto dto, String sub) {
        partes_trabajo parte = partes_trabajo_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));

        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        if (!esGestor && !parte.getPerfil().getId().equals(solicitante.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes editar partes de otros usuarios");
        }

        if (!esGestor) {
            boolean esHoy = parte.getFecha().isEqual(LocalDate.now());
            boolean esFechaLibre = configuration_service.fechaPermitida(sub, parte.getFecha());

            if (!esHoy && !esFechaLibre) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo puedes editar partes de dias habilitados");
            }
        }

        // Actualizar obra si se especifica
        if (dto.id_obra() != null) {
            obra obra = obra_repo.findById(dto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Obra no encontrada"));
            parte.setObra(obra);
        }

        // Actualizar fecha si se especifica (con validaciones)
        if (dto.fecha() != null) {
            if (ausenciasService.estaAusenteEnFecha(parte.getPerfil().getId(), dto.fecha())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se puede mover el parte: el operario esta de baja o vacaciones ese dia");
            }

            if (!esGestor) {
                boolean nuevaEsHoy = dto.fecha().isEqual(LocalDate.now());
                boolean nuevaEsFechaLibre = configuration_service.fechaPermitida(sub, dto.fecha());
                if (!nuevaEsHoy && !nuevaEsFechaLibre) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No tienes permisos para mover el parte a la fecha solicitada");
                }
            }
            parte.setFecha(dto.fecha());
        }

        // Actualizar campos opcionales
        if (dto.horas_normales() != null)  parte.setHoras_normales(dto.horas_normales());
        if (dto.descripcion() != null)     parte.setDescripcion(dto.descripcion());
        if (dto.especialidad() != null)    parte.setEspecialidad(dto.especialidad());
        if (dto.trabajo_extra() != null)   parte.setTrabajos_extra(dto.trabajo_extra());
        if (dto.nombre_firmado() != null)  parte.setNombre_firmado(dto.nombre_firmado());

        // Actualizar firma si se envia una nueva (borrando la anterior)
        if (dto.firma_base64() != null && !dto.firma_base64().isBlank()) {
            if (parte.getFirma_url() != null) {
                borrarFirmaStorage(parte.getFirma_url());
            }
            String firmaUrl = subirFirmaBase64(dto.firma_base64(), parte, parte.getObra(), parte.getPerfil());
            parte.setFirma_url(firmaUrl);
        }

        return partes_trabajo_repo.save(parte);
    }

    // ─── FECHAS CON PARTE ─────────────────────────────────────────────────────

    /*
     * Obtiene las fechas en las que un trabajador tiene partes registrados.
     * Recibe: el UUID del trabajador (como String)
     * Devuelve: lista de fechas ordenadas cronologicamente
     */
    public List<LocalDate> getFechasConParte(String id) {
        UUID uuid = UUID.fromString(id);
        return partes_trabajo_repo.findDistinctFechasByPerfilId(uuid);
    }

    /*
     * Obtiene las fechas con parte del usuario autenticado.
     * Recibe: sub (UUID del token JWT)
     * Devuelve: lista de fechas
     */
    public List<LocalDate> getFechasConPartePorUsername(String sub) {
        return getFechasConParte(sub);
    }

    // ─── METODOS PRIVADOS AUXILIARES ──────────────────────────────────────────

    /*
     * Sube una imagen de firma en formato base64 a Supabase Storage.
     *
     * Recibe:
     * - base64: la imagen codificada en base64 (con o sin prefijo "data:")
     * - parte: el parte de trabajo al que pertenece la firma
     * - obra: la obra donde se trabajo
     * - perfil: el trabajador que firma
     *
     * Devuelve: la URL publica de la imagen subida
     *
     * La imagen se guarda en el bucket "firmas-partes" organizada en
     * carpetas por obra y con nombre que incluye trabajador y fecha.
     */
    private String subirFirmaBase64(String base64, partes_trabajo parte, obra obra, perfil perfil) {
        String contentType = "image/png";
        String datos = base64;

        // Extraer el tipo de contenido y los datos si vienen con prefijo "data:image/..."
        if (base64.startsWith("data:")) {
            String[] split = base64.split(",", 2);
            contentType = split[0].replace("data:", "").replace(";base64", "");
            datos = split[1];
        }

        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(datos);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo firma_base64 no es un Base64 valido");
        }

        String extension = contentType.contains("png") ? "png" : "jpg";
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String nombreObra = slugify(obra.getNombre());
        String nombreUsuario = slugify(perfil.getName() + "_" + perfil.getApellidos());

        // Ruta del archivo: obra/nombreObra_nombreUsuario_idParte_fecha.extension
        String objectPath = String.format("%s/%s_%s_id%d_%s.%s",
                nombreObra, nombreObra, nombreUsuario, parte.getId(), timestamp, extension);

        String uploadUrl = supabaseUrl + "/storage/v1/object/" + BUCKET_FIRMAS + "/" + objectPath;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Content-Type", contentType)
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error al subir la firma al bucket: " + response.body());
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error de conexion al subir la firma");
        }

        return supabasePublicUrl + "/storage/v1/object/public/" + BUCKET_FIRMAS + "/" + objectPath;
    }

    /*
     * Convierte un texto en un formato apto para nombres de archivo:
     * - Elimina tildes y caracteres especiales
     * - Convierte a minusculas
     * - Reemplaza espacios y caracteres no alfanumericos por guion bajo
     */
    private String slugify(String input) {
        if (input == null) return "desconocido";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    /*
     * Elimina un archivo de firma de Supabase Storage.
     *
     * Recibe: la URL publica de la firma a eliminar
     *
     * Si la URL no corresponde al bucket de firmas, no hace nada.
     * Los errores se ignoran para no bloquear la operacion principal.
     */
    private void borrarFirmaStorage(String firmaUrl) {
        if (firmaUrl == null || firmaUrl.isBlank()) return;
        try {
            String prefix = supabasePublicUrl + "/storage/v1/object/public/" + BUCKET_FIRMAS + "/";
            if (!firmaUrl.startsWith(prefix)) return;
            String objectPath = firmaUrl.substring(prefix.length());

            String deleteUrl = supabaseUrl + "/storage/v1/object/" + BUCKET_FIRMAS + "/" + objectPath;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .DELETE()
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            // Error no critico: se ignora para no interrumpir al usuario
        }
    }
}