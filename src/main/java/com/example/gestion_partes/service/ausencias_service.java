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

    /**
     * Calcula los días laborables (L-V) sin parte para cada operario/encargado
     * activo en la quincena actual.
     *
     * Quincena:
     *   - Del 1 al 15 del mes en curso
     *   - Del 16 al último día del mes en curso
     * Solo cuenta hasta hoy (no días futuros).
     */
    public Map<String, Object> getDiasSinParte() {
        LocalDate hoy = LocalDate.now();

        // ── Calcular rango de la quincena actual ──────────────────────────
        final LocalDate inicio;
        final LocalDate fin;
        if (hoy.getDayOfMonth() <= 15) {
            inicio = hoy.withDayOfMonth(1);
            LocalDate finQuincena = hoy.withDayOfMonth(15);
            fin = finQuincena.isAfter(hoy) ? hoy : finQuincena;
        } else {
            inicio = hoy.withDayOfMonth(16);
            LocalDate finMes = hoy.withDayOfMonth(hoy.getMonth().length(hoy.isLeapYear()));
            fin = finMes.isAfter(hoy) ? hoy : finMes;
        }

        // ── Días laborables del período ────────────────────────────────────
        List<LocalDate> diasLaborables = diasLaborablesEntre(inicio, fin);

        // ── Operarios y encargados activos ────────────────────────────────
        List<perfil> operarios = perfil_repo.findAll().stream()
                .filter(p -> p.isActivo())
                .filter(p -> p.getRol() == user_rol.OPERARIO
                        || p.getRol() == user_rol.ENCARGADO)
                .collect(Collectors.toList());

        // ── Partes del período agrupados por perfil ───────────────────────
        List<partes_trabajo> partesPeriodo = partes_trabajo_repo
                .findAll().stream()
                .filter(p -> !p.getFecha().isBefore(inicio) && !p.getFecha().isAfter(fin))
                .collect(Collectors.toList());

        Map<UUID, Set<LocalDate>> fechasPorPerfil = new HashMap<>();
        for (partes_trabajo p : partesPeriodo) {
            fechasPorPerfil
                    .computeIfAbsent(p.getPerfil().getId(), k -> new HashSet<>())
                    .add(p.getFecha());
        }

        // ── Calcular ausencias ────────────────────────────────────────────
        // Resultado ordenado alfabéticamente por apellidos
        Map<String, Object> resultado = new LinkedHashMap<>();

        operarios.stream()
                .sorted(Comparator.comparing(p -> p.getApellidos() + " " + p.getName()))
                .forEach(p -> {
                    Set<LocalDate> conParte = fechasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptySet());

                    List<String> diasSin = diasLaborables.stream()
                            .filter(d -> !conParte.contains(d))
                            .map(d -> d.format(FMT))
                            .collect(Collectors.toList());

                    // Solo incluir si tiene al menos 1 día sin parte
                    if (!diasSin.isEmpty()) {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("nombre", p.getName() + " " + p.getApellidos());
                        info.put("diasSin", diasSin);
                        info.put("totalSin", diasSin.size());
                        info.put("totalLaborables", diasLaborables.size());
                        resultado.put(p.getId().toString(), info);
                    }
                });

        return resultado;
    }

    /**
     * Devuelve todos los días de lunes a viernes entre [inicio] y [fin] inclusive.
     */
    private List<LocalDate> diasLaborablesEntre(LocalDate inicio, LocalDate fin) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fin)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                dias.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dias;
    }
}