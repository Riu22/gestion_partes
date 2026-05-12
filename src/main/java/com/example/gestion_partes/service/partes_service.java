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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class partes_service {

    @Autowired partes_trabajo_repo partes_trabajo_repo;
    @Autowired perfil_repo perfil_repo;
    @Autowired obra_repo obra_repo;
    @Autowired configuration_service configuration_service;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.public.url}")
    private String supabasePublicUrl;

    @Value("${supabase.service.key}")
    private String supabaseServiceKey;

    private static final String BUCKET_FIRMAS = "firmas-partes";

    // ─── Crear parte ──────────────────────────────────────────────────────────

    public partes_trabajo create_parte(partes_dto dto, String sub) {
        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;

        if (!esGestor) {
            LocalDate limiteMinimo = LocalDate.now().minusWeeks(2);
            if (dto.fecha().isBefore(limiteMinimo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No puedes crear partes con más de 2 semanas de antigüedad");
            }
        }

        if (!esGestor && !solicitante.getId().equals(dto.id_perfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes crear partes para ti mismo");
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

        if (!obra.isActiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear partes en una obra inactiva");
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

        boolean creadoParaOtro = !solicitante.getId().equals(idPerfil);
        nuevo.setCreado_por_gestor(esGestor && creadoParaOtro);

        // Primer save para obtener el id generado por la BD
        partes_trabajo guardado = partes_trabajo_repo.save(nuevo);

        // Si viene firma en base64, subirla al bucket y guardar la URL
        if (dto.firma_base64() != null && !dto.firma_base64().isBlank()) {
            String firmaUrl = subirFirmaBase64(dto.firma_base64(), guardado, obra, perfil);
            guardado.setFirma_url(firmaUrl);
            guardado = partes_trabajo_repo.save(guardado);
        }

        return guardado;
    }

    // ─── Firmar parte a posteriori ────────────────────────────────────────────
    // Para casos en los que el parte ya existe y se firma después

    public partes_trabajo firmar_parte(Long idParte, String firmaBase64) {

        partes_trabajo parte = partes_trabajo_repo.findById(idParte)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));

        if (parte.getFirma_url() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Este parte ya ha sido firmado");
        }

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La firma no puede estar vacía");
        }

        String firmaUrl = subirFirmaBase64(firmaBase64, parte,
                parte.getObra(), parte.getPerfil());
        parte.setFirma_url(firmaUrl);
        return partes_trabajo_repo.save(parte);
    }

    // ─── Listar partes ────────────────────────────────────────────────────────

    public List<partes_trabajo> get_partes_jerarquico(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.ADMINISTRACION
                || usuario.getRol() == user_rol.GESTION) {
            return partes_trabajo_repo.findAll();
        }

        List<partes_trabajo> resultado = new ArrayList<>(
                partes_trabajo_repo.findByPerfilId(usuario.getId()));

        if (usuario.getRol() == user_rol.JEFE_DE_OBRA) {
            resultado.addAll(partes_trabajo_repo.findPartesParaJefeObra(usuario.getId()));
        } else if (usuario.getRol() == user_rol.ENCARGADO) {
            resultado.addAll(partes_trabajo_repo.findPartesParaEncargado(usuario.getId()));
        }

        return resultado.stream()
                .collect(Collectors.toMap(partes_trabajo::getId, p -> p, (a, b) -> a))
                .values()
                .stream()
                .toList();
    }

    // ─── Eliminar parte ───────────────────────────────────────────────────────

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
            boolean esFechaLibre = configuration_service.fechaPermitida(
                    sub, parte.getFecha());

            if (!esHoy && !esFechaLibre) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo puedes eliminar partes de días habilitados");
            }
        }

        partes_trabajo_repo.deleteById(parteId);
    }

    // ─── Actualizar parte ─────────────────────────────────────────────────────

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
            boolean esFechaLibre = configuration_service.fechaPermitida(
                    sub, parte.getFecha());

            if (!esHoy && !esFechaLibre) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo puedes editar partes de días habilitados");
            }
        }

        if (dto.id_obra() != null) {
            obra obra = obra_repo.findById(dto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Obra no encontrada"));
            parte.setObra(obra);
        }
        if (dto.fecha() != null) parte.setFecha(dto.fecha());
        if (dto.horas_normales() != null) parte.setHoras_normales(dto.horas_normales());
        if (dto.descripcion() != null) parte.setDescripcion(dto.descripcion());
        if (dto.especialidad() != null) parte.setEspecialidad(dto.especialidad());

        return partes_trabajo_repo.save(parte);
    }

    // ─── Fechas con parte ─────────────────────────────────────────────────────

    public List<LocalDate> getFechasConParte(String id) {
        UUID uuid = UUID.fromString(id);
        return partes_trabajo_repo.findByPerfilId(uuid)
                .stream()
                .map(partes_trabajo::getFecha)
                .distinct()
                .sorted()
                .toList();
    }

    public List<LocalDate> getFechasConPartePorUsername(String sub) {
        return getFechasConParte(sub);
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────

    private String subirFirmaBase64(String base64, partes_trabajo parte, obra obra, perfil perfil) {

        String contentType = "image/png";
        String datos = base64;

        // Soporta con y sin cabecera "data:image/png;base64,..."
        if (base64.startsWith("data:")) {
            String[] split = base64.split(",", 2);
            contentType = split[0].replace("data:", "").replace(";base64", "");
            datos = split[1];
        }

        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(datos);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo firma_base64 no es un Base64 válido");
        }

        String extension = contentType.contains("png") ? "png" : "jpg";
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String nombreObra = slugify(obra.getNombre());
        String nombreUsuario = slugify(perfil.getName() + "_" + perfil.getApellidos());

        // {obra}/{obra}_{operario}_id{id}_{dd-MM-yyyy}.ext
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

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error al subir la firma al bucket: " + response.body());
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error de conexión al subir la firma");
        }

        return supabasePublicUrl + "/storage/v1/object/public/" + BUCKET_FIRMAS + "/" + objectPath;
    }

    private String slugify(String input) {
        if (input == null) return "desconocido";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}