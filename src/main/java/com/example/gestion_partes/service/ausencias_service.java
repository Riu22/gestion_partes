package com.example.gestion_partes.service;

import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ausencias_service {

    @Autowired
    private partes_trabajo_repo partes_trabajo_repo;

    @Autowired
    private perfil_repo perfil_repo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1),
            MonthDay.of(1,  6),
            MonthDay.of(5,  1),
            MonthDay.of(8,  15),
            MonthDay.of(10, 12),
            MonthDay.of(11, 1),
            MonthDay.of(12, 6),
            MonthDay.of(12, 8),
            MonthDay.of(12, 25)
    );

    private boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_FIJOS.contains(MonthDay.from(fecha));
    }

    /**
     * Para cada operario/encargado activo devuelve:
     *   - diasSin: días laborables sin ningún parte
     *   - diasIncompletos: días con parte pero con horas totales < 8
     *     cada elemento es un Map con "fecha" y "horas"
     */
    public Map<String, Object> getDiasSinParte() {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);

        final LocalDate inicio;
        final LocalDate fin;
        if (hoy.getDayOfMonth() <= 15) {
            inicio = hoy.withDayOfMonth(1);
            LocalDate finQuincena = hoy.withDayOfMonth(15);
            fin = finQuincena.isAfter(ayer) ? ayer : finQuincena;
        } else {
            inicio = hoy.withDayOfMonth(16);
            LocalDate finMes = hoy.withDayOfMonth(
                    hoy.getMonth().length(hoy.isLeapYear()));
            fin = finMes.isAfter(ayer) ? ayer : finMes;
        }

        if (fin.isBefore(inicio)) return Collections.emptyMap();

        List<LocalDate> diasLaborables = diasLaborablesEntre(inicio, fin);

        // Operarios y encargados activos
        List<perfil> operarios = perfil_repo.findAll().stream()
                .filter(p -> p.isActivo())
                .filter(p -> p.getRol() == user_rol.OPERARIO
                        || p.getRol() == user_rol.ENCARGADO)
                .collect(Collectors.toList());

        // Partes del período
        List<partes_trabajo> partesPeriodo = partes_trabajo_repo.findAll().stream()
                .filter(p -> !p.getFecha().isBefore(inicio)
                        && !p.getFecha().isAfter(fin))
                .collect(Collectors.toList());

        // Agrupar partes por perfil → fecha → suma de horas
        // Map<perfilId, Map<fecha, totalHoras>>
        Map<UUID, Map<LocalDate, Double>> horasPorPerfilFecha = new HashMap<>();
        for (partes_trabajo p : partesPeriodo) {
            horasPorPerfilFecha
                    .computeIfAbsent(p.getPerfil().getId(), k -> new HashMap<>())
                    .merge(p.getFecha(),
                            p.getHoras_normales() != null ? p.getHoras_normales() : 0.0,
                            Double::sum);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();

        operarios.stream()
                .sorted(Comparator.comparing(
                        p -> p.getApellidos() + " " + p.getName()))
                .forEach(p -> {
                    Map<LocalDate, Double> horasPorFecha = horasPorPerfilFecha
                            .getOrDefault(p.getId(), Collections.emptyMap());

                    // Días sin ningún parte
                    List<String> diasSin = diasLaborables.stream()
                            .filter(d -> !horasPorFecha.containsKey(d))
                            .map(d -> d.format(FMT))
                            .collect(Collectors.toList());

                    // Días con parte pero horas < 8
                    List<Map<String, Object>> diasIncompletos = diasLaborables.stream()
                            .filter(d -> horasPorFecha.containsKey(d)
                                    && horasPorFecha.get(d) < 8.0)
                            .map(d -> {
                                double h = horasPorFecha.get(d);
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("fecha", d.format(FMT));
                                // Formato: "6" o "6.5"
                                entry.put("horas", h % 1 == 0
                                        ? String.valueOf((int) h)
                                        : String.valueOf(h));
                                return entry;
                            })
                            .collect(Collectors.toList());

                    // Solo incluir si hay algo que reportar
                    if (!diasSin.isEmpty() || !diasIncompletos.isEmpty()) {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("nombre", p.getName() + " " + p.getApellidos());
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