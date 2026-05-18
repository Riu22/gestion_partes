package com.example.gestion_partes.service;

import com.example.gestion_partes.model.Ausencia;
import com.example.gestion_partes.model.AusenciaTipo;
import com.example.gestion_partes.repo.AusenciaRepo;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ausencias_service {

    @Autowired private partes_trabajo_repo partes_trabajo_repo;
    @Autowired private perfil_repo perfil_repo;
    @Autowired private AusenciaRepo ausenciaRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1), MonthDay.of(1,  6),
            MonthDay.of(5,  1), MonthDay.of(8,  15),
            MonthDay.of(10, 12), MonthDay.of(11, 1),
            MonthDay.of(12, 6), MonthDay.of(12, 8),
            MonthDay.of(12, 25)
    );

    // ── Ausencias laborales ───────────────────────────────────────────────────

    public Ausencia crear(UUID perfilId, AusenciaTipo tipo,
                          LocalDate inicio, LocalDate fin,
                          String observaciones) {
        return ausenciaRepo.save(new Ausencia(perfilId, tipo, inicio, fin, observaciones));
    }

    public void eliminar(Long id) {
        ausenciaRepo.deleteById(id);
    }

    public List<Ausencia> getDeUsuario(UUID perfilId) {
        return ausenciaRepo.findByPerfilIdOrderByFechaInicioDesc(perfilId);
    }

    public Map<UUID, List<Ausencia>> getAusenciasEnRango(LocalDate inicio, LocalDate fin) {
        return ausenciaRepo.findTodasEnRango(inicio, fin)
                .stream()
                .collect(Collectors.groupingBy(Ausencia::getPerfilId));
    }

    public boolean estaAusenteEnFecha(UUID perfilId, LocalDate fecha) {
        return !ausenciaRepo.findSolapadasEnRango(perfilId, fecha, fecha).isEmpty();
    }

    // ── Incidencias ───────────────────────────────────────────────────────────

    public Map<String, Object> getDiasSinParte() {
        LocalDate fin = LocalDate.now().minusDays(1);
        LocalDate inicio = partes_trabajo_repo.findFechaMasAntigua().orElse(fin);
        if (fin.isBefore(inicio)) return Collections.emptyMap();

        List<LocalDate> diasLaborables = diasLaborablesEntre(inicio, fin);

        List<perfil> operarios = perfil_repo.findByActivoTrueAndRolIn(
                List.of(user_rol.OPERARIO, user_rol.ENCARGADO)
        );

        Map<UUID, Map<LocalDate, Double>> horasPorPerfilFecha = new HashMap<>();
        partes_trabajo_repo.findHorasPorPerfilYFecha(inicio, fin)
                .forEach(row -> {
                    UUID perfilId = (UUID) row[0];
                    LocalDate fecha = (LocalDate) row[1];
                    Double horas = ((Number) row[2]).doubleValue();
                    horasPorPerfilFecha
                            .computeIfAbsent(perfilId, k -> new HashMap<>())
                            .put(fecha, horas);
                });

        // Cargamos todas las ausencias del rango en una sola query
        Map<UUID, List<Ausencia>> ausenciasPorPerfil =
                getAusenciasEnRango(inicio, fin);

        Map<String, Object> resultado = new LinkedHashMap<>();

        operarios.stream()
                .sorted(Comparator.comparing(p -> p.getApellidos() + " " + p.getName()))
                .forEach(p -> {
                    Map<LocalDate, Double> horasPorFecha = horasPorPerfilFecha
                            .getOrDefault(p.getId(), Collections.emptyMap());

                    // Conjunto de fechas cubiertas por baja o vacaciones
                    Set<LocalDate> diasJustificados = ausenciasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptyList())
                            .stream()
                            .flatMap(a -> a.getFechaInicio()
                                    .datesUntil(a.getFechaFin().plusDays(1)))
                            .collect(Collectors.toSet());

                    // Ausencias activas para mostrar en la UI
                    List<Map<String, Object>> ausenciasActivas = ausenciasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptyList())
                            .stream()
                            .map(a -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", a.getId());
                                m.put("tipo", a.getTipo().name());
                                m.put("fechaInicio", a.getFechaInicio().format(FMT));
                                m.put("fechaFin", a.getFechaFin().format(FMT));
                                m.put("observaciones", a.getObservaciones());
                                return m;
                            })
                            .collect(Collectors.toList());

                    List<String> diasSin = diasLaborables.stream()
                            .filter(d -> !horasPorFecha.containsKey(d))
                            .filter(d -> !diasJustificados.contains(d)) // ← excluir justificados
                            .map(d -> d.format(FMT))
                            .collect(Collectors.toList());

                    List<Map<String, Object>> diasIncompletos = diasLaborables.stream()
                            .filter(d -> horasPorFecha.containsKey(d)
                                    && horasPorFecha.get(d) < 8.0)
                            .filter(d -> !diasJustificados.contains(d)) // ← excluir justificados
                            .map(d -> {
                                double h = horasPorFecha.get(d);
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("fecha", d.format(FMT));
                                entry.put("horas", h % 1 == 0
                                        ? String.valueOf((int) h)
                                        : String.valueOf(h));
                                return entry;
                            })
                            .collect(Collectors.toList());

                    if (!diasSin.isEmpty() || !diasIncompletos.isEmpty()
                            || !ausenciasActivas.isEmpty()) {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("nombre", p.getName() + " " + p.getApellidos());
                        info.put("ausenciasActivas", ausenciasActivas);
                        info.put("diasSin", diasSin);
                        info.put("totalSin", diasSin.size());
                        info.put("diasIncompletos", diasIncompletos);
                        info.put("totalIncompletos", diasIncompletos.size());
                        info.put("totalLaborables", diasLaborables.size());
                        resultado.put(p.getId().toString(), info);
                    }
                });

        return resultado;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_FIJOS.contains(MonthDay.from(fecha));
    }

    private List<LocalDate> diasLaborablesEntre(LocalDate inicio, LocalDate fin) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fin)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY
                    && dow != DayOfWeek.SUNDAY
                    && !esFestivo(cursor)) {
                dias.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dias;
    }
}