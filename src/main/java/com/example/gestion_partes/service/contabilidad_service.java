package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.Ausencia;
import com.example.gestion_partes.model.AusenciaTipo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.AusenciaRepo;
import com.example.gestion_partes.repo.obra_repo;
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

    private static final String OBRA_LUM        = "OFICINA LUM/ALMACÉN LUM";
    private static final String BASE_URL_PARTE  = "/partes/";

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
    @Autowired private obra_repo           obrasRepo;

    private record AusenciaDia(String tipo, Long obraId) {}

    // ── API pública ───────────────────────────────────────────────────────────

    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partesRepo.getResumenQuincena(desde, hasta);
    }

    /** Administración: ve todo sin filtro de obras ni de jefe. */
    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        return procesarDatos(
                partesRepo.getDetalleContabilidad(desde, hasta),
                desde, hasta, null, null);
    }

    /**
     * Vista de jefe de obra: filtra por sus obras PERO además incluye
     * todo lo que ha hecho su personal directo (jefe_directo_id = jefeId)
     * aunque sea en obras ajenas.
     *
     * @param jefeId  UUID del jefe que hace la consulta (null si es admin/gestión)
     */
    public List<Map<String, Object>> getDetalleContabilidadPorObras(
            LocalDate desde, LocalDate hasta, List<Long> obraIds, UUID jefeId) {
        return procesarDatos(
                partesRepo.getDetalleContabilidadPorObras(desde, hasta, obraIds, jefeId),
                desde, hasta, obraIds, jefeId);
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

    private Map<String, Object> entradaDia(double horas, Long parteId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horas",    horas);
        m.put("parte_id", parteId);
        m.put("link",     parteId != null ? BASE_URL_PARTE + parteId : null);
        return m;
    }

    private String resolverNombreObra(AusenciaDia ad, Map<Long, String> nombreObras) {
        if ("VACACIONES".equals(ad.tipo()) && ad.obraId() != null) {
            return nombreObras.getOrDefault(ad.obraId(), OBRA_LUM);
        }
        return OBRA_LUM;
    }

    private boolean esFilaDeVacacionesConObra(
            String nombreObra,
            perfil p,
            Map<UUID, Map<LocalDate, AusenciaDia>> ausenciasFecha,
            Map<Long, String> nombreObras) {

        if (p == null) return false;
        return ausenciasFecha
                .getOrDefault(p.getId(), Collections.emptyMap())
                .values().stream()
                .anyMatch(ad -> "VACACIONES".equals(ad.tipo())
                        && ad.obraId() != null
                        && nombreObras.getOrDefault(ad.obraId(), "").equals(nombreObra));
    }

    // ── Procesado principal ───────────────────────────────────────────────────

    private List<Map<String, Object>> procesarDatos(
            List<contabilidad_detalle_dto> datos,
            LocalDate desde,
            LocalDate hasta,
            List<Long> obraIds,
            UUID jefeId) {

        boolean esAdministracion = (obraIds == null);

        // ── 0. Resolver personal propio del jefe ──────────────────────────────
        //      Incluye subordinados directos Y subordinados de sus encargados
        //      (dos niveles, igual que la query SQL).
        Set<String> codigosPersonalPropio = new HashSet<>();
        Set<UUID>   idsPersonalPropio     = new HashSet<>();

        if (jefeId != null) {
            // Nivel 1: jefe_directo_id = jefeId  (operarios/encargados directos)
            List<perfil> nivel1 = perfilRepo.findByJefeDirecto_Id(jefeId);
            for (perfil p : nivel1) {
                if (p.getCodigo() != null) codigosPersonalPropio.add(p.getCodigo());
                idsPersonalPropio.add(p.getId());

                // Nivel 2: personal cuyo jefe_directo es un encargado de nivel 1
                if (p.getRol() == user_rol.ENCARGADO) {
                    for (perfil p2 : perfilRepo.findByJefeDirecto_Id(p.getId())) {
                        if (p2.getCodigo() != null) codigosPersonalPropio.add(p2.getCodigo());
                        idsPersonalPropio.add(p2.getId());
                    }
                }
            }
        }

        // ── 1. Cargar perfiles indexados ──────────────────────────────────────
        List<perfil> todosPerfiles = perfilRepo.findAll();

        Map<String, perfil> codigoAPerfil = new HashMap<>(todosPerfiles.size() * 2);
        Map<UUID,   perfil> idAPerfil     = new HashMap<>(todosPerfiles.size() * 2);
        Set<String> codigosConParte       = new HashSet<>();

        for (perfil p : todosPerfiles) {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
            if (p.getId()     != null) idAPerfil    .put(p.getId(),     p);
        }

        // ── 1b. Nombres de obras ──────────────────────────────────────────────
        Map<Long, String> nombreObras = obrasRepo.findAll()
                .stream()
                .collect(Collectors.toMap(
                        o -> o.getId(),
                        o -> o.getNombre()
                ));

        // ── 2. Agrupar filas del query principal ──────────────────────────────
        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObraRaw   = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String especialidad    = d.getEspecialidad() != null
                    ? d.getEspecialidad().toUpperCase() : "";
            boolean esFont         = "FONTANERIA".equals(especialidad);
            String nombreObraVista = esFont ? "Font " + nombreObraRaw : nombreObraRaw;

            String codigoUser = d.getCodigo() != null ? d.getCodigo() : "000";
            String clave      = codigoUser + "|" + nombreObraVista;

            codigosConParte.add(codigoUser);

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
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) horasDia.get(fechaStr);
                    double acum = ((Number) existing.get("horas")).doubleValue() + horas;
                    existing.put("horas", acum);
                } else {
                    horasDia.put(fechaStr, entradaDia(horas, parteId));
                }
            }
            mapaAgrupado.get(clave)
                    .merge("total_horas", horas, (a, b) -> (double) a + (double) b);
        }

        // ── 3. Inyectar operarios sin partes ──────────────────────────────────
        //   - Administración: todos los operarios/encargados activos
        //   - Jefe de obra:   solo su personal propio (codigosPersonalPropio)
        boolean inyectarSinPartes = esAdministracion || !codigosPersonalPropio.isEmpty();

        if (inyectarSinPartes) {
            for (perfil p : todosPerfiles) {
                if (p.getCodigo() == null)                                   continue;
                if (p.getRol() != user_rol.OPERARIO
                        && p.getRol() != user_rol.ENCARGADO)                 continue;
                if (!p.isActivo())                                           continue;
                if (codigosConParte.contains(p.getCodigo()))                 continue;

                // En vista de jefe: solo inyectamos su personal propio
                if (!esAdministracion
                        && !codigosPersonalPropio.contains(p.getCodigo()))   continue;

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

        // ── 4. Pre-indexar ausencias ──────────────────────────────────────────
        //   - Administración: todas las ausencias del rango
        //   - Jefe de obra:   solo las de su personal propio
        List<Ausencia> ausenciasRango = ausenciaRepo.findTodasEnRango(desde, hasta);

        if (!esAdministracion && !idsPersonalPropio.isEmpty()) {
            ausenciasRango = ausenciasRango.stream()
                    .filter(a -> idsPersonalPropio.contains(a.getPerfilId()))
                    .collect(Collectors.toList());
        }

        Map<UUID, Map<LocalDate, AusenciaDia>> ausenciasFecha = new HashMap<>();

        for (Ausencia a : ausenciasRango) {
            Map<LocalDate, AusenciaDia> porFecha =
                    ausenciasFecha.computeIfAbsent(a.getPerfilId(), id -> new HashMap<>());

            for (LocalDate d = a.getFechaInicio();
                 !d.isAfter(a.getFechaFin()); d = d.plusDays(1)) {
                if (esLaborable(d)) {
                    porFecha.putIfAbsent(d,
                            new AusenciaDia(a.getTipo().name(), a.getObraId()));
                }
            }
        }

        // ── 5. Inyectar filas de ausencia ─────────────────────────────────────
        //   Igual que antes, pero en vista de jefe también aplica para su personal.
        if (esAdministracion || !codigosPersonalPropio.isEmpty()) {
            for (Map.Entry<UUID, Map<LocalDate, AusenciaDia>> entry
                    : ausenciasFecha.entrySet()) {

                UUID   perfilId = entry.getKey();
                perfil p        = idAPerfil.get(perfilId);
                if (p == null || p.getCodigo() == null) continue;

                Set<String> obrasNecesarias = entry.getValue().values().stream()
                        .map(ad -> resolverNombreObra(ad, nombreObras))
                        .collect(Collectors.toSet());

                for (String nombreObra : obrasNecesarias) {
                    String claveObra = p.getCodigo() + "|" + nombreObra;

                    if (!mapaAgrupado.containsKey(claveObra)) {
                        String aps = p.getApellidos() != null
                                ? p.getApellidos().toUpperCase() : "";
                        String nom = p.getName() != null ? p.getName() : "S/N";

                        Map<String, Object> filaObra = new LinkedHashMap<>();
                        filaObra.put("codigo",               p.getCodigo());
                        filaObra.put("operario",             aps.isEmpty() ? nom : aps + ", " + nom);
                        filaObra.put("obra",                 nombreObra);
                        filaObra.put("categoria_profesional",
                                p.getGrupo_profesional() != null
                                        ? p.getGrupo_profesional() : "No asignado");
                        filaObra.put("horas_por_dia",        new LinkedHashMap<>());
                        filaObra.put("total_horas",          0.0);
                        mapaAgrupado.put(claveObra, filaObra);
                    }
                }

                mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
            }
        }

        // ── 6. Rellenar ausencias_por_dia ─────────────────────────────────────
        for (Map<String, Object> fila : mapaAgrupado.values()) {
            fila.putIfAbsent("ausencias_por_dia", new LinkedHashMap<String, String>());

            String obraFila = (String) fila.get("obra");
            String codigo   = (String) fila.get("codigo");
            perfil p        = codigoAPerfil.get(codigo);

            boolean esFilaAusencia = OBRA_LUM.equals(obraFila)
                    || esFilaDeVacacionesConObra(obraFila, p, ausenciasFecha, nombreObras);

            if (!esFilaAusencia) continue;
            if (p == null)       continue;

            Map<LocalDate, AusenciaDia> ausFecha =
                    ausenciasFecha.getOrDefault(p.getId(), Collections.emptyMap());

            @SuppressWarnings("unchecked")
            Map<String, String> ausenciasPorDia =
                    (Map<String, String>) fila.get("ausencias_por_dia");

            for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                AusenciaDia ad = ausFecha.get(dia);
                if (ad == null) continue;

                String obraEsperada = resolverNombreObra(ad, nombreObras);
                if (obraEsperada.equals(obraFila)) {
                    ausenciasPorDia.put(dia.toString(), ad.tipo());
                }
            }
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}