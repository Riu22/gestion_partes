package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.Ausencia;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.AusenciaRepo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class contabilidad_service {

    private static final String OBRA_LUM = "OFICINA LUM/ALMACÉN LUM";
    private static final String BASE_URL_PARTE = "/partes/";

    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1), MonthDay.of(1,  6),
            MonthDay.of(5,  1), MonthDay.of(8, 15),
            MonthDay.of(10,12), MonthDay.of(11, 1),
            MonthDay.of(12, 6), MonthDay.of(12, 8),
            MonthDay.of(12,25)
    );

    @Autowired private partes_trabajo_repo partesRepo;
    @Autowired private AusenciaRepo        ausenciaRepo;
    @Autowired private perfil_repo         perfilRepo;

    // ── API pública ───────────────────────────────────────────────────────────

    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partesRepo.getResumenQuincena(desde, hasta);
    }

    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        return procesarDatos(
                partesRepo.getDetalleContabilidad(desde, hasta),
                desde, hasta, null);
    }

    public List<Map<String, Object>> getDetalleContabilidadPorObras(
            LocalDate desde, LocalDate hasta, List<Long> obraIds) {
        return procesarDatos(
                partesRepo.getDetalleContabilidadPorObras(desde, hasta, obraIds),
                desde, hasta, obraIds);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_FIJOS.contains(MonthDay.from(fecha));
    }

    private boolean esLaborable(LocalDate fecha) {
        DayOfWeek dow = fecha.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY
                && dow != DayOfWeek.SUNDAY
                && !esFestivo(fecha);
    }

    /** Construye el objeto día con horas, parte_id y link. */
    private Map<String, Object> entradaDia(double horas, Long parteId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horas",    horas);
        m.put("parte_id", parteId);
        m.put("link",     parteId != null ? BASE_URL_PARTE + parteId : null);
        return m;
    }

    // ── Procesado principal ───────────────────────────────────────────────────

    private List<Map<String, Object>> procesarDatos(
            List<contabilidad_detalle_dto> datos,
            LocalDate desde,
            LocalDate hasta,
            List<Long> obraIds) {

        boolean esAdministracion = (obraIds == null);

        // ── 1. Cargar perfiles UNA sola vez, indexados por código y por UUID ──
        //    Antes: findAll() + findByActivoTrue() = 2 queries completas.
        //    Ahora: una sola query trae todo; filtramos en memoria.
        List<perfil> todosPerfiles = perfilRepo.findAll();

        Map<String, perfil> codigoAPerfil = new HashMap<>(todosPerfiles.size() * 2);
        Map<UUID,   perfil> idAPerfil     = new HashMap<>(todosPerfiles.size() * 2);
        // Set de códigos con parte en el rango → para detectar "sin parte" en O(1)
        Set<String> codigosConParte       = new HashSet<>();

        for (perfil p : todosPerfiles) {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
            if (p.getId()     != null) idAPerfil.put(p.getId(), p);
        }

        // ── 2. Agrupar filas del query principal ──────────────────────────────
        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObraRaw = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String especialidad  = d.getEspecialidad() != null
                    ? d.getEspecialidad().toUpperCase() : "";
            boolean esFont       = "FONTANERIA".equals(especialidad);
            String nombreObraVista = esFont ? "Font " + nombreObraRaw : nombreObraRaw;

            String codigoUser = d.getCodigo() != null ? d.getCodigo() : "000";
            String clave      = codigoUser + "|" + nombreObraVista;

            codigosConParte.add(codigoUser); // registrar que tiene al menos un parte

            mapaAgrupado.computeIfAbsent(clave, k -> {
                String aps = d.getApellidos() != null ? d.getApellidos().toUpperCase() : "";
                String nom = d.getNombre()    != null ? d.getNombre()                  : "S/N";

                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("codigo",               codigoUser);
                fila.put("operario",             aps.isEmpty() ? nom : aps + ", " + nom);
                fila.put("obra",                 nombreObraVista);
                fila.put("categoria_profesional",
                        d.getGrupo_profesional() != null
                                ? d.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",        new LinkedHashMap<String, Object>());
                fila.put("total_horas",          0.0);
                return fila;
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> horasDia =
                    (Map<String, Object>) mapaAgrupado.get(clave).get("horas_por_dia");

            LocalDate fechaKey = d.getFecha();
            double    horas    = d.getHoras_totales() != null ? d.getHoras_totales() : 0.0;
            Long      parteId  = d.getParteId();

            if (fechaKey != null) {
                String fechaStr = fechaKey.toString();
                if (horasDia.containsKey(fechaStr)) {
                    // acumular horas; el link apunta al parte más reciente del día
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) horasDia.get(fechaStr);
                    double acum = ((Number) existing.get("horas")).doubleValue() + horas;
                    existing.put("horas", acum);
                    // mantiene el parte_id del primer registro (o actualiza si prefieres)
                } else {
                    horasDia.put(fechaStr, entradaDia(horas, parteId));
                }
            }
            mapaAgrupado.get(clave)
                    .merge("total_horas", horas, (a, b) -> (double) a + (double) b);
        }

        // ── 3. Inyectar operarios sin partes (solo administración) ────────────
        //    Antes: stream().anyMatch() por cada perfil = O(perfiles × claves).
        //    Ahora: lookup en HashSet = O(1).
        if (esAdministracion) {
            for (perfil p : todosPerfiles) {
                if (p.getCodigo() == null) continue;
                if (p.getRol() != user_rol.OPERARIO && p.getRol() != user_rol.ENCARGADO) continue;
                if (!p.isActivo()) continue;
                if (codigosConParte.contains(p.getCodigo())) continue; // ya tiene fila

                String claveSinParte = p.getCodigo() + "|__SIN_PARTE__";
                String aps = p.getApellidos() != null ? p.getApellidos().toUpperCase() : "";
                String nom = p.getName()      != null ? p.getName()                    : "S/N";

                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("codigo",               p.getCodigo());
                fila.put("operario",             aps.isEmpty() ? nom : aps + ", " + nom);
                fila.put("obra",                 "");
                fila.put("categoria_profesional",
                        p.getGrupo_profesional() != null
                                ? p.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",        new LinkedHashMap<>());
                fila.put("total_horas",          0.0);
                fila.put("ausencias_por_dia",    new LinkedHashMap<String, String>());
                mapaAgrupado.put(claveSinParte, fila);
            }
        }

        // ── 4. Ausencias ──────────────────────────────────────────────────────
        //    Antes: stream anidado O(ausencias) por cada día laborable.
        //    Ahora: pre-indexar ausencias como Map<UUID, Map<LocalDate, String>>
        //    para lookup O(1) por fecha.
        Map<UUID, Map<LocalDate, String>> ausenciasFecha = new HashMap<>();

        for (Ausencia a : ausenciaRepo.findTodasEnRango(desde, hasta)) {
            Map<LocalDate, String> porFecha =
                    ausenciasFecha.computeIfAbsent(a.getPerfilId(), id -> new HashMap<>());
            for (LocalDate d = a.getFechaInicio();
                 !d.isAfter(a.getFechaFin()); d = d.plusDays(1)) {
                if (esLaborable(d)) porFecha.putIfAbsent(d, a.getTipo().name());
            }
        }

        // ── 5. Inyectar filas LUM para perfiles con ausencia ─────────────────
        if (esAdministracion) {
            for (UUID perfilId : ausenciasFecha.keySet()) {
                perfil p = idAPerfil.get(perfilId);
                if (p == null || p.getCodigo() == null) continue;

                String claveLum = p.getCodigo() + "|" + OBRA_LUM;
                if (!mapaAgrupado.containsKey(claveLum)) {
                    String aps = p.getApellidos() != null ? p.getApellidos().toUpperCase() : "";
                    String nom = p.getName()      != null ? p.getName()                    : "S/N";

                    Map<String, Object> filaLum = new LinkedHashMap<>();
                    filaLum.put("codigo",               p.getCodigo());
                    filaLum.put("operario",             aps.isEmpty() ? nom : aps + ", " + nom);
                    filaLum.put("obra",                 OBRA_LUM);
                    filaLum.put("categoria_profesional",
                            p.getGrupo_profesional() != null
                                    ? p.getGrupo_profesional() : "No asignado");
                    filaLum.put("horas_por_dia",        new LinkedHashMap<>());
                    filaLum.put("total_horas",          0.0);
                    mapaAgrupado.put(claveLum, filaLum);
                }
                mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
            }
        }

        // ── 6. Rellenar ausencias_por_dia en filas LUM ────────────────────────
        for (Map<String, Object> fila : mapaAgrupado.values()) {
            fila.putIfAbsent("ausencias_por_dia", new LinkedHashMap<String, String>());

            if (!OBRA_LUM.equals(fila.get("obra"))) continue;

            String codigo = (String) fila.get("codigo");
            perfil p      = codigoAPerfil.get(codigo);
            if (p == null) continue;

            Map<LocalDate, String> ausFecha =
                    ausenciasFecha.getOrDefault(p.getId(), Collections.emptyMap());

            @SuppressWarnings("unchecked")
            Map<String, String> ausenciasPorDia =
                    (Map<String, String>) fila.get("ausencias_por_dia");

            // O(días del rango) con lookup O(1) — antes era O(días × ausencias)
            for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                String tipo = ausFecha.get(dia); // null si no hay ausencia ese día
                if (tipo != null) ausenciasPorDia.put(dia.toString(), tipo);
            }
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}