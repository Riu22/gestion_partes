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

    private static final String OBRA_LUM    = "OFICINA LUM/ALMACÉN LUM";
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
    @Autowired private obra_repo          obrasRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // Record interno: tipo de ausencia + obra asociada (solo VACACIONES)
    // ─────────────────────────────────────────────────────────────────────────

    private record AusenciaDia(String tipo, Long obraId) {}

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

    /**
     * Devuelve el nombre de obra que debe usarse para imputar una ausencia.
     * - VACACIONES con obraId → nombre de esa obra
     * - Cualquier otro caso   → OBRA_LUM
     */
    private String resolverNombreObra(AusenciaDia ad, Map<Long, String> nombreObras) {
        if ("VACACIONES".equals(ad.tipo()) && ad.obraId() != null) {
            return nombreObras.getOrDefault(ad.obraId(), OBRA_LUM);
        }
        return OBRA_LUM;
    }

    /**
     * Comprueba si una fila (identificada por su nombre de obra) fue generada
     * como destino de unas vacaciones con obra asignada para el perfil dado.
     */
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
            List<Long> obraIds) {

        boolean esAdministracion = (obraIds == null);

        // ── 1. Cargar perfiles UNA sola vez, indexados por código y por UUID ──
        List<perfil> todosPerfiles = perfilRepo.findAll();

        Map<String, perfil> codigoAPerfil = new HashMap<>(todosPerfiles.size() * 2);
        Map<UUID,   perfil> idAPerfil     = new HashMap<>(todosPerfiles.size() * 2);
        Set<String> codigosConParte       = new HashSet<>();

        for (perfil p : todosPerfiles) {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
            if (p.getId()     != null) idAPerfil    .put(p.getId(),     p);
        }

        // ── 1b. Precargar nombres de obras (para resolver VACACIONES con obra) ─
        Map<Long, String> nombreObras = obrasRepo.findAll()
                .stream()
                .collect(Collectors.toMap(
                        o -> o.getId(),
                        o -> o.getNombre()
                ));

        // ── 2. Agrupar filas del query principal ──────────────────────────────
        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObraRaw  = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String especialidad   = d.getEspecialidad() != null
                    ? d.getEspecialidad().toUpperCase() : "";
            boolean esFont        = "FONTANERIA".equals(especialidad);
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

        // ── 3. Inyectar operarios sin partes (solo administración) ────────────
        if (esAdministracion) {
            for (perfil p : todosPerfiles) {
                if (p.getCodigo() == null)                                   continue;
                if (p.getRol() != user_rol.OPERARIO
                        && p.getRol() != user_rol.ENCARGADO)                 continue;
                if (!p.isActivo())                                           continue;
                if (codigosConParte.contains(p.getCodigo()))                 continue;

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

        // ── 4. Pre-indexar ausencias: Map<perfilId, Map<fecha, AusenciaDia>> ──
        //      Guardamos tipo + obraId para poder resolver destino en paso 5.
        Map<UUID, Map<LocalDate, AusenciaDia>> ausenciasFecha = new HashMap<>();

        for (Ausencia a : ausenciaRepo.findTodasEnRango(desde, hasta)) {
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

        // ── 5. Inyectar filas de ausencia para cada perfil ────────────────────
        //      - BAJA / PATERNIDAD / VACACIONES sin obra → fila OBRA_LUM
        //      - VACACIONES con obraId                   → fila con nombre de esa obra
        if (esAdministracion) {
            for (Map.Entry<UUID, Map<LocalDate, AusenciaDia>> entry
                    : ausenciasFecha.entrySet()) {

                UUID   perfilId = entry.getKey();
                perfil p        = idAPerfil.get(perfilId);
                if (p == null || p.getCodigo() == null) continue;

                // Nombres de obra distintos que necesita este perfil
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

                // Ya no necesitamos la fila placeholder sin parte
                mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
            }
        }

        // ── 6. Rellenar ausencias_por_dia ─────────────────────────────────────
        //      Se rellena en filas OBRA_LUM Y en filas de vacaciones con obra.
        for (Map<String, Object> fila : mapaAgrupado.values()) {
            fila.putIfAbsent("ausencias_por_dia", new LinkedHashMap<String, String>());

            String obraFila = (String) fila.get("obra");
            String codigo   = (String) fila.get("codigo");
            perfil p        = codigoAPerfil.get(codigo);

            // Solo rellenamos ausencias en filas que son destino de ausencia:
            // - la fila de OBRA_LUM
            // - una fila de vacaciones con obra asignada
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

                // Solo anotamos en esta fila si la ausencia de este día
                // corresponde a la obra de la fila actual.
                String obraEsperada = resolverNombreObra(ad, nombreObras);
                if (obraEsperada.equals(obraFila)) {
                    ausenciasPorDia.put(dia.toString(), ad.tipo());
                }
            }
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}