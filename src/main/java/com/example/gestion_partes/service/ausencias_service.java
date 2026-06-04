/*
 * SERVICIO: ausencias_service (Gestion de ausencias laborales e incidencias)
 *
 * Gestiona todo lo relacionado con las ausencias de los trabajadores
 * (bajas medicas, vacaciones, permisos de paternidad) y el control
 * de incidencias (dias sin parte, dias incompletos).
 *
 * Metodos principales:
 *
 * AUSENCIAS LABORALES:
 * - crear:             Registra una nueva ausencia para un trabajador
 * - eliminar:          Elimina un registro de ausencia
 * - getDeUsuario:      Obtiene todas las ausencias de un trabajador
 * - getAusenciasEnRango: Obtiene todas las ausencias en un periodo de fechas
 * - estaAusenteEnFecha: Comprueba si un trabajador estaba ausente en una fecha concreta
 *
 * INCIDENCIAS:
 * - getDiasSinParte:   Detecta los trabajadores que tienen dias sin registrar
 *                      o con menos de 8 horas, generando un informe completo
 *                      de incidencias para administracion
 * - getHistorialPerfil: Obtiene el historial completo de un trabajador:
 *                      sus dias laborables, los que falto sin justificar,
 *                      los que tuvo incompletos y sus ausencias
 */
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

    // Lista de festivos fijos nacionales en Espana
    private static final Set<MonthDay> FESTIVOS_FIJOS = Set.of(
            MonthDay.of(1,  1),   // Ano Nuevo
            MonthDay.of(1,  6),   // Reyes
            MonthDay.of(5,  1),   // Dia del Trabajador
            MonthDay.of(8,  15),  // Asuncion
            MonthDay.of(10, 12),  // Hispanidad
            MonthDay.of(11, 1),   // Todos los Santos
            MonthDay.of(12, 6),   // Constitucion
            MonthDay.of(12, 8),   // Inmaculada
            MonthDay.of(12, 25)   // Navidad
    );

    // ─── AUSENCIAS LABORALES ──────────────────────────────────────────────────

    /*
     * Crea un nuevo registro de ausencia para un trabajador.
     *
     * Recibe:
     * - perfilId: UUID del trabajador ausente
     * - tipo: tipo de ausencia (BAJA, VACACIONES, PATERNIDAD)
     * - inicio: primer dia de la ausencia
     * - fin: ultimo dia de la ausencia
     * - observaciones: notas adicionales (opcional)
     * - obraId: ID de la obra a la que se imputa (solo para VACACIONES, opcional)
     *
     * Devuelve: el objeto Ausencia creado y guardado en BD
     */
    public Ausencia crear(UUID perfilId, AusenciaTipo tipo,
                          LocalDate inicio, LocalDate fin,
                          String observaciones, Long obraId) {

        Ausencia a = new Ausencia(perfilId, tipo, inicio, fin, observaciones);

        // Si son vacaciones y se especifica una obra, se asocia la ausencia a esa obra
        if (tipo == AusenciaTipo.VACACIONES && obraId != null) {
            a.setObraId(obraId);
        }

        return ausenciaRepo.save(a);
    }

    /*
     * Elimina un registro de ausencia por su ID.
     */
    public void eliminar(Long id) {
        ausenciaRepo.deleteById(id);
    }

    /*
     * Obtiene todas las ausencias de un trabajador, ordenadas de la mas
     * reciente a la mas antigua.
     */
    public List<Ausencia> getDeUsuario(UUID perfilId) {
        return ausenciaRepo.findByPerfilIdOrderByFechaInicioDesc(perfilId);
    }

    /*
     * Obtiene todas las ausencias de todos los trabajadores en un rango
     * de fechas. Las devuelve agrupadas por trabajador (UUID).
     */
    public Map<UUID, List<Ausencia>> getAusenciasEnRango(LocalDate inicio, LocalDate fin) {
        return ausenciaRepo.findTodasEnRango(inicio, fin)
                .stream()
                .collect(Collectors.groupingBy(Ausencia::getPerfilId));
    }

    /*
     * Comprueba si un trabajador estaba ausente en una fecha concreta.
     * Busca si hay alguna ausencia cuyo rango de fechas incluya la fecha indicada.
     *
     * Recibe: el UUID del trabajador y la fecha a comprobar
     * Devuelve: true si estaba ausente, false si no
     */
    public boolean estaAusenteEnFecha(UUID perfilId, LocalDate fecha) {
        return !ausenciaRepo.findSolapadasEnRango(perfilId, fecha, fecha).isEmpty();
    }

    // ───INCIDENCIAS ───────────────────────────────────────────────────────────

    /*
     * Genera un informe completo de incidencias para todos los operarios
     * y encargados activos. Detecta:
     *
     * - Dias sin parte: dias laborables en los que el trabajador NO registro
     *   ningun parte de trabajo y no tiene una ausencia justificada
     * - Dias incompletos: dias laborables en los que el trabajador registro
     *   menos de 8 horas y no tiene ausencia justificada
     * - Ausencias activas: periodos de ausencia visibles en la UI
     *   (mes actual y mes anterior)
     *
     * El resultado se ordena por el primer dia sin parte (los mas urgentes primero)
     * y luego alfabeticamente por apellidos.
     *
     * Devuelve: un mapa donde cada clave es el UUID del trabajador y el valor
     * es un mapa con su nombre, dias sin, dias incompletos y ausencias activas.
     */
    public Map<String, Object> getDiasSinParte() {
        LocalDate fin = LocalDate.now().minusDays(1);

        // Primer dia del mes anterior (limite para mostrar ausencias en la UI)
        LocalDate primerDiaMesAnterior = LocalDate.now()
                .withDayOfMonth(1)
                .minusMonths(1);

        // Obtener todos los operarios y encargados activos
        List<perfil> operarios = perfil_repo.findByActivoTrueAndRolIn(
                List.of(user_rol.OPERARIO, user_rol.ENCARGADO)
        );

        if (operarios.isEmpty()) return Collections.emptyMap();

        // Fecha mas antigua entre todos los perfiles (para precargar datos optimizados)
        LocalDate inicioGlobal = operarios.stream()
                .map(p -> p.getCreadoEl() != null
                        ? p.getCreadoEl().toLocalDate()
                        : fin)
                .min(Comparator.naturalOrder())
                .orElse(fin);

        if (fin.isBefore(inicioGlobal)) return Collections.emptyMap();

        // Cargar todas las horas de todos los operarios en una sola consulta
        Map<UUID, Map<LocalDate, Double>> horasPorPerfilFecha = new HashMap<>();
        partes_trabajo_repo.findHorasPorPerfilYFecha(inicioGlobal, fin)
                .forEach(row -> {
                    UUID perfilId = (UUID) row[0];
                    LocalDate fecha = (LocalDate) row[1];
                    Double horas = ((Number) row[2]).doubleValue();
                    horasPorPerfilFecha
                            .computeIfAbsent(perfilId, k -> new HashMap<>())
                            .put(fecha, horas);
                });

        // Cargar todas las ausencias en una sola consulta
        Map<UUID, List<Ausencia>> ausenciasPorPerfil = getAusenciasEnRango(inicioGlobal, fin);

        Map<String, Object> resultado = new LinkedHashMap<>();

        // Procesar cada operario, ordenados por urgencia (primero los que llevan mas dias sin parte)
        operarios.stream()
                .sorted(Comparator
                        .comparing((perfil p) -> {
                            Map<LocalDate, Double> horas = horasPorPerfilFecha
                                    .getOrDefault(p.getId(), Collections.emptyMap());
                            LocalDate inicioPerfil = p.getCreadoEl() != null
                                    ? p.getCreadoEl().toLocalDate() : fin;
                            return diasLaborablesEntre(inicioPerfil, fin).stream()
                                    .filter(d -> !horas.containsKey(d) ||
                                            (horas.containsKey(d) && horas.get(d) < 8.0))
                                    .min(Comparator.naturalOrder())
                                    .orElse(LocalDate.MAX);
                        })
                        .thenComparing(p -> p.getApellidos() + " " + p.getName()))
                .forEach(p -> {
                    LocalDate inicioPerfil = p.getCreadoEl() != null
                            ? p.getCreadoEl().toLocalDate()
                            : fin;

                    if (fin.isBefore(inicioPerfil)) return;

                    List<LocalDate> diasLaborables = diasLaborablesEntre(inicioPerfil, fin);

                    Map<LocalDate, Double> horasPorFecha = horasPorPerfilFecha
                            .getOrDefault(p.getId(), Collections.emptyMap());

                    // Conjunto completo de fechas justificadas por ausencias (toda la historia)
                    Set<LocalDate> diasJustificados = ausenciasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptyList())
                            .stream()
                            .flatMap(a -> a.getFechaInicio()
                                    .datesUntil(a.getFechaFin().plusDays(1)))
                            .collect(Collectors.toSet());

                    // Ausencias visibles en la UI: solo mes actual + mes anterior
                    List<Map<String, Object>> ausenciasActivas = ausenciasPorPerfil
                            .getOrDefault(p.getId(), Collections.emptyList())
                            .stream()
                            .filter(a -> !a.getFechaFin().isBefore(primerDiaMesAnterior))
                            .map(a -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id",            a.getId());
                                m.put("tipo",          a.getTipo().name());
                                m.put("fechaInicio",   a.getFechaInicio().format(FMT));
                                m.put("fechaFin",      a.getFechaFin().format(FMT));
                                m.put("observaciones", a.getObservaciones());
                                return m;
                            })
                            .collect(Collectors.toList());

                    // Dias laborables sin ningun parte registrado
                    List<String> diasSin = diasLaborables.stream()
                            .filter(d -> !horasPorFecha.containsKey(d))
                            .filter(d -> !diasJustificados.contains(d))
                            .map(d -> d.format(FMT))
                            .collect(Collectors.toList());

                    // Dias laborables con parte pero con menos de 8 horas
                    List<Map<String, Object>> diasIncompletos = diasLaborables.stream()
                            .filter(d -> horasPorFecha.containsKey(d)
                                    && horasPorFecha.get(d) < 8.0)
                            .filter(d -> !diasJustificados.contains(d))
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

                    // Solo incluir en el resultado si tiene algo que notificar
                    if (!diasSin.isEmpty() || !diasIncompletos.isEmpty()
                            || !ausenciasActivas.isEmpty()) {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("nombre",           p.getApellidos() + " " + p.getName());
                        info.put("ausenciasActivas", ausenciasActivas);
                        info.put("diasSin",          diasSin);
                        info.put("totalSin",         diasSin.size());
                        info.put("diasIncompletos",  diasIncompletos);
                        info.put("totalIncompletos", diasIncompletos.size());
                        info.put("totalLaborables",  diasLaborables.size());
                        resultado.put(p.getId().toString(), info);
                    }
                });

        return resultado;
    }

    // ─── HISTORIAL COMPLETO DE UN PERFIL ──────────────────────────────────────

    /*
     * Obtiene el historial completo de un trabajador: sus dias laborables,
     * los dias que falto sin justificar, los dias con menos de 8 horas,
     * y todas sus ausencias registradas.
     *
     * Recibe: el UUID del trabajador
     * Devuelve: un mapa con toda la informacion del historial
     *   - nombre: nombre completo
     *   - perfilId: UUID
     *   - totalLaborables: total de dias laborables desde su incorporacion
     *   - diasSin: lista de fechas sin parte
     *   - totalSin: cantidad de dias sin parte
     *   - diasIncompletos: lista de fechas con menos de 8h
     *   - totalIncompletos: cantidad de dias incompletos
     *   - ausencias: lista de ausencias registradas
     */
    public Map<String, Object> getHistorialPerfil(UUID perfilId) {
        perfil p = perfil_repo.findById(perfilId)
                .orElseThrow(() -> new RuntimeException("Perfil no encontrado: " + perfilId));

        LocalDate fin          = LocalDate.now().minusDays(1);
        LocalDate inicioPerfil = p.getCreadoEl() != null
                ? p.getCreadoEl().toLocalDate()
                : fin;

        // Si el trabajador se acaba de incorporar, devolver historial vacio
        if (fin.isBefore(inicioPerfil)) {
            Map<String, Object> vacio = new LinkedHashMap<>();
            vacio.put("nombre",           p.getApellidos() + " " + p.getName());
            vacio.put("perfilId",         perfilId.toString());
            vacio.put("totalLaborables",  0);
            vacio.put("diasSin",          Collections.emptyList());
            vacio.put("totalSin",         0);
            vacio.put("diasIncompletos",  Collections.emptyList());
            vacio.put("totalIncompletos", 0);
            vacio.put("ausencias",        Collections.emptyList());
            return vacio;
        }

        // Dias laborables desde la incorporacion hasta ayer
        List<LocalDate> diasLaborables = diasLaborablesEntre(inicioPerfil, fin);

        // Horas registradas en partes de trabajo
        Map<LocalDate, Double> horasPorFecha = new HashMap<>();
        partes_trabajo_repo.findHorasPorPerfilYFecha(inicioPerfil, fin)
                .forEach(row -> horasPorFecha.put(
                        (LocalDate) row[1],
                        ((Number) row[2]).doubleValue()));

        // Todas las ausencias historicas del perfil
        List<Ausencia> ausencias = ausenciaRepo
                .findByPerfilIdOrderByFechaInicioDesc(perfilId);

        // Conjunto completo de dias justificados por ausencias
        Set<LocalDate> diasJustificados = ausencias.stream()
                .flatMap(a -> a.getFechaInicio()
                        .datesUntil(a.getFechaFin().plusDays(1)))
                .collect(Collectors.toSet());

        // Dias laborables sin parte registrado
        List<String> diasSin = diasLaborables.stream()
                .filter(d -> !horasPorFecha.containsKey(d))
                .filter(d -> !diasJustificados.contains(d))
                .map(d -> d.format(FMT))
                .collect(Collectors.toList());

        // Dias laborables con menos de 8 horas registradas
        List<Map<String, Object>> diasIncompletos = diasLaborables.stream()
                .filter(d -> horasPorFecha.containsKey(d)
                        && horasPorFecha.get(d) < 8.0)
                .filter(d -> !diasJustificados.contains(d))
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

        // Formatear ausencias para la respuesta
        List<Map<String, Object>> ausenciasDto = ausencias.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",            a.getId());
                    m.put("tipo",          a.getTipo().name());
                    m.put("fechaInicio",   a.getFechaInicio().format(FMT));
                    m.put("fechaFin",      a.getFechaFin().format(FMT));
                    m.put("observaciones", a.getObservaciones());
                    return m;
                })
                .collect(Collectors.toList());

        // Construir respuesta
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("nombre",           p.getApellidos() + " " + p.getName());
        resultado.put("perfilId",         perfilId.toString());
        resultado.put("totalLaborables",  diasLaborables.size());
        resultado.put("diasSin",          diasSin);
        resultado.put("totalSin",         diasSin.size());
        resultado.put("diasIncompletos",  diasIncompletos);
        resultado.put("totalIncompletos", diasIncompletos.size());
        resultado.put("ausencias",        ausenciasDto);
        return resultado;
    }

    // ─── METODOS PRIVADOS AUXILIARES ─────────────────────────────────────────

    /*
     * Comprueba si una fecha concreta es festivo nacional.
     * Recibe: una fecha
     * Devuelve: true si es festivo
     */
    private boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_FIJOS.contains(MonthDay.from(fecha));
    }

    /*
     * Calcula todos los dias laborables entre dos fechas (inclusive),
     * excluyendo sabados, domingos y festivos nacionales.
     *
     * Recibe: fecha de inicio y fecha de fin
     * Devuelve: lista de dias laborables
     */
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