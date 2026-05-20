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

    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1), MonthDay.of(1,  6),
            MonthDay.of(5,  1), MonthDay.of(8,  15),
            MonthDay.of(10, 12), MonthDay.of(11, 1),
            MonthDay.of(12, 6), MonthDay.of(12, 8),
            MonthDay.of(12, 25)
    );

    @Autowired private partes_trabajo_repo partes_trabajo_repo;
    @Autowired private AusenciaRepo ausenciaRepo;
    @Autowired private perfil_repo perfil_repo;

    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partes_trabajo_repo.getResumenQuincena(desde, hasta);
    }

    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        return procesarDatos(
                partes_trabajo_repo.getDetalleContabilidad(desde, hasta),
                desde, hasta, null);
    }

    public List<Map<String, Object>> getDetalleContabilidadPorObras(
            LocalDate desde, LocalDate hasta, List<Long> obraIds) {
        return procesarDatos(
                partes_trabajo_repo.getDetalleContabilidadPorObras(desde, hasta, obraIds),
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

    // ── Procesado principal ───────────────────────────────────────────────────

    /**
     * @param obraIds  Si no es null, estamos en modo JEFE_DE_OBRA y solo
     *                 inyectamos operarios activos asignados a esas obras.
     *                 Si es null, inyectamos TODOS los operarios activos.
     */
    private List<Map<String, Object>> procesarDatos(
            List<contabilidad_detalle_dto> datos,
            LocalDate desde,
            LocalDate hasta,
            List<Long> obraIds) {

        Map<String, Map<String, Object>> mapaAgrupado = new LinkedHashMap<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObraRaw = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String especialidad  = d.getEspecialidad() != null
                    ? d.getEspecialidad().toUpperCase() : "";
            boolean esFont       = "FONTANERIA".equals(especialidad);

            String nombreObraVista = esFont ? "Font " + nombreObraRaw : nombreObraRaw;

            String codigoUser = d.getCodigo() != null ? d.getCodigo() : "000";
            String clave = codigoUser + "|" + nombreObraVista;

            mapaAgrupado.computeIfAbsent(clave, k -> {
                String aps = d.getApellidos() != null ? d.getApellidos().toUpperCase() : "";
                String nom = d.getNombre()    != null ? d.getNombre()                  : "S/N";
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("codigo",               codigoUser);
                fila.put("operario",             operarioFull);
                fila.put("obra",                 nombreObraVista);
                fila.put("categoria_profesional",
                        d.getGrupo_profesional() != null
                                ? d.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",        new HashMap<LocalDate, Double>());
                fila.put("total_horas",          0.0);
                return fila;
            });

            @SuppressWarnings("unchecked")
            Map<LocalDate, Double> horasDia =
                    (Map<LocalDate, Double>) mapaAgrupado.get(clave).get("horas_por_dia");

            LocalDate fechaKey = d.getFecha();
            double horas = d.getHoras_totales() != null ? d.getHoras_totales() : 0.0;

            if (fechaKey != null) {
                horasDia.merge(fechaKey, horas, Double::sum);
            }
            mapaAgrupado.get(clave)
                    .merge("total_horas", horas, (a, b) -> (double) a + (double) b);
        }

        // ── Mapas de perfiles ─────────────────────────────────────────────────

        Map<String, perfil> codigoAPerfil = new HashMap<>();
        perfil_repo.findAll().forEach(p -> {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
        });

        Map<UUID, perfil> idAPerfil = new HashMap<>();
        codigoAPerfil.values().forEach(p -> idAPerfil.put(p.getId(), p));

        // ── Inyectar operarios activos que no tienen ningún parte ────────────
        //
        //  Se crea una fila con obra = "" y horas_por_dia vacío para que el
        //  frontend los muestre con todo en '-' pero aparezcan en la tabla
        //  y en el filtro de operarios.
        //
        List<perfil> perfilesActivos = perfil_repo.findByActivoTrue()
                .stream()
                .filter(p -> p.getRol() == user_rol.OPERARIO
                        || p.getRol() == user_rol.ENCARGADO)
                .collect(Collectors.toList());

        for (perfil p : perfilesActivos) {
            if (p.getCodigo() == null) continue;

            // ¿Ya tiene al menos una fila en el mapa (con partes)?
            final String codigo = p.getCodigo();
            boolean tieneFila = mapaAgrupado.keySet().stream()
                    .anyMatch(k -> k.startsWith(codigo + "|"));

            if (!tieneFila) {
                String claveSinParte = codigo + "|__SIN_PARTE__";
                String aps = p.getApellidos() != null ? p.getApellidos().toUpperCase() : "";
                String nom = p.getName()      != null ? p.getName()                    : "S/N";
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("codigo",               codigo);
                fila.put("operario",             operarioFull);
                fila.put("obra",                 "");   // sin obra asignada en ese periodo
                fila.put("categoria_profesional",
                        p.getGrupo_profesional() != null
                                ? p.getGrupo_profesional() : "No asignado");
                fila.put("horas_por_dia",        new HashMap<LocalDate, Double>());
                fila.put("total_horas",          0.0);
                fila.put("ausencias_por_dia",    new LinkedHashMap<String, String>());
                mapaAgrupado.put(claveSinParte, fila);
            }
        }

        // ── Ausencias por día ─────────────────────────────────────────────────

        Map<UUID, List<Ausencia>> ausenciasPorPerfil =
                ausenciaRepo.findTodasEnRango(desde, hasta)
                        .stream()
                        .collect(Collectors.groupingBy(Ausencia::getPerfilId));

        // ── Inyectar filas sintéticas LUM para operarios con ausencia ─────────
        for (UUID perfilId : ausenciasPorPerfil.keySet()) {
            perfil p = idAPerfil.get(perfilId);
            if (p == null || p.getCodigo() == null) continue;

            String claveLum = p.getCodigo() + "|" + OBRA_LUM;
            if (!mapaAgrupado.containsKey(claveLum)) {
                String aps = p.getApellidos() != null ? p.getApellidos().toUpperCase() : "";
                String nom = p.getName()      != null ? p.getName()                    : "S/N";
                String operarioFull = aps.isEmpty() ? nom : aps + ", " + nom;

                Map<String, Object> filaLum = new LinkedHashMap<>();
                filaLum.put("codigo",               p.getCodigo());
                filaLum.put("operario",             operarioFull);
                filaLum.put("obra",                 OBRA_LUM);
                filaLum.put("categoria_profesional",
                        p.getGrupo_profesional() != null
                                ? p.getGrupo_profesional() : "No asignado");
                filaLum.put("horas_por_dia",        new HashMap<LocalDate, Double>());
                filaLum.put("total_horas",          0.0);
                mapaAgrupado.put(claveLum, filaLum);
            }

            // Si el operario tenía fila "__SIN_PARTE__" y ahora tiene fila LUM,
            // eliminamos la sintética vacía para no duplicar
            mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
        }

        // ── Rellenar ausencias_por_dia solo en filas LUM (días laborables) ────
        for (Map<String, Object> fila : mapaAgrupado.values()) {
            String obraFila = (String) fila.get("obra");
            boolean esLum   = OBRA_LUM.equals(obraFila);

            // Solo inicializar si aún no tiene la clave (las filas __SIN_PARTE__
            // ya la llevan puesta desde su creación)
            fila.putIfAbsent("ausencias_por_dia", new LinkedHashMap<String, String>());

            if (esLum) {
                String codigo = (String) fila.get("codigo");
                perfil p      = codigoAPerfil.get(codigo);
                if (p != null) {
                    List<Ausencia> ausencias = ausenciasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptyList());

                    @SuppressWarnings("unchecked")
                    Map<String, String> ausenciasPorDia =
                            (Map<String, String>) fila.get("ausencias_por_dia");

                    for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                        if (!esLaborable(dia)) continue;
                        final LocalDate diaFinal = dia;
                        ausencias.stream()
                                .filter(a -> !diaFinal.isBefore(a.getFechaInicio())
                                        && !diaFinal.isAfter(a.getFechaFin()))
                                .findFirst()
                                .ifPresent(a -> ausenciasPorDia.put(
                                        diaFinal.toString(), a.getTipo().name()));
                    }
                }
            }
        }

        return new ArrayList<>(mapaAgrupado.values());
    }
}