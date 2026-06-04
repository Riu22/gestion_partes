/*
 * SERVICIO: contabilidad_service (Procesamiento de datos de contabilidad)
 *
 * Este servicio procesa los datos de partes de trabajo para generar
 * informes de contabilidad: resumenes quincenales y detalle dia a dia
 * de las horas trabajadas por cada operario en cada obra.
 *
 * Es el servicio mas complejo en cuanto a procesamiento de datos,
 * con multiples pasos de transformacion y optimizacion de consultas
 * a base de datos para minimizar el numero de viajes.
 *
 * Metodos principales:
 *
 * - getResumenQuincena:     Resumen agrupado de horas por trabajador y obra
 *                           en un periodo de 15 dias
 * - getDetalleContabilidad: Informe detallado dia a dia para administracion
 * - getDetalleContabilidadPorObras: Informe detallado filtrado por obras y
 *                           jerarquia (para jefes de obra)
 *
 * METODO INTERNO (procesarDatos):
 * Procesa los datos en 7 pasos optimizados:
 *   0. Cargar subordinados del jefe (para filtrar)
 *   1. Cargar perfiles en un solo viaje a BD
 *   1b. Preparar mapa de nombres de obras (diferido)
 *   2. Agrupar filas por trabajador + obra
 *   3. Anadir trabajadores sin partes registrados
 *   4. Cargar y procesar ausencias
 *   5. Crear filas de ausencia en el resultado
 *   6. Rellenar las marcas de ausencia por dia
 *   7. Convertir a formato de salida
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.Ausencia;
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

    // Nombre por defecto para la obra "oficina" cuando se imputan ausencias
    private static final String OBRA_LUM       = "OFICINA LUM/ALMACEN LUM";
    // URL base para enlaces a partes individuales
    private static final String BASE_URL_PARTE = "/partes/";

    // Valores "centinela" para evitar consultas SQL invalidas con IN () vacio
    private static final String       CODIGO_CENTINELA = "__VACIO__";
    private static final UUID         UUID_CENTINELA   =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    // Festivos nacionales fijos
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

    // Registro interno para almacenar tipo de ausencia y obra asociada
    private record AusenciaDia(String tipo, Long obraId) {}

    /*
     * Clase interna que representa una fila del informe de contabilidad.
     * Contiene los datos de un trabajador en una obra, con las horas
     * de cada dia y las ausencias.
     */
    private static final class FilaContabilidad {
        final String codigo;
        final String operario;
        final String obra;
        final String categoriaProfesional;
        final Map<String, Map<String, Object>> horasPorDia    = new LinkedHashMap<>();
        final Map<String, String>              ausenciasPorDia = new LinkedHashMap<>();
        double totalHoras = 0.0;

        FilaContabilidad(String codigo, String operario,
                         String obra,  String categoriaProfesional) {
            this.codigo               = codigo;
            this.operario             = operario;
            this.obra                 = obra;
            this.categoriaProfesional = categoriaProfesional;
        }

        // Convierte la fila a un mapa para la respuesta JSON
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo",               codigo);
            m.put("operario",             operario);
            m.put("obra",                 obra);
            m.put("categoria_profesional",categoriaProfesional);
            m.put("horas_por_dia",        horasPorDia);
            m.put("total_horas",          totalHoras);
            m.put("ausencias_por_dia",    ausenciasPorDia);
            return m;
        }
    }

    // ─── API PUBLICA ──────────────────────────────────────────────────────────

    /*
     * Obtiene el resumen de una quincena: horas totales por trabajador y obra.
     * Recibe: fecha de inicio y fin del periodo
     * Devuelve: lista de quincena_dto (codigo, nombre, apellidos, obra, total_horas)
     */
    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partesRepo.getResumenQuincena(desde, hasta);
    }

    /*
     * Obtiene el detalle de contabilidad para un periodo (administracion).
     * Incluye todas las horas de todos los trabajadores dia por dia.
     *
     * Recibe: fecha de inicio y fin
     * Devuelve: lista de filas con codigo, operario, obra, categoria,
     *           horas_por_dia, total_horas y ausencias_por_dia
     */
    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        long t0 = System.currentTimeMillis();
        List<contabilidad_detalle_dto> datos = partesRepo.getDetalleContabilidad(desde, hasta);
        System.out.printf("[PERF] query principal:     %4d ms  (%d filas)%n",
                System.currentTimeMillis() - t0, datos.size());
        return procesarDatos(datos, desde, hasta, null, null, t0);
    }

    /*
     * Obtiene el detalle de contabilidad filtrado para un jefe de obra.
     * Solo incluye las obras asignadas al jefe y los trabajadores
     * bajo su supervision (directa e indirecta).
     *
     * Recibe: periodo, IDs de obras del jefe y UUID del jefe
     * Devuelve: lista de filas de contabilidad filtrada
     */
    public List<Map<String, Object>> getDetalleContabilidadPorObras(
            LocalDate desde, LocalDate hasta, List<Long> obraIds, UUID jefeId) {
        long t0 = System.currentTimeMillis();
        List<contabilidad_detalle_dto> datos =
                partesRepo.getDetalleContabilidadPorObras(desde, hasta, obraIds, jefeId);
        System.out.printf("[PERF] query principal:     %4d ms  (%d filas)%n",
                System.currentTimeMillis() - t0, datos.size());
        return procesarDatos(datos, desde, hasta, obraIds, jefeId, t0);
    }

    // ─── METODOS AUXILIARES DE FECHAS ─────────────────────────────────────────

    // Comprueba si una fecha es festivo nacional
    private boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_FIJOS.contains(MonthDay.from(fecha));
    }

    // Comprueba si una fecha es laborable (no sabado, no domingo, no festivo)
    private boolean esLaborable(LocalDate fecha) {
        DayOfWeek dow = fecha.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY
                && dow != DayOfWeek.SUNDAY
                && !esFestivo(fecha);
    }

    // Crea una entrada de horas para un dia concreto
    private Map<String, Object> entradaDia(double horas, Long parteId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horas",    horas);
        m.put("parte_id", parteId);
        m.put("link",     parteId != null ? BASE_URL_PARTE + parteId : null);
        return m;
    }

    /*
     * Resuelve el nombre de la obra para una ausencia.
     * Si es VACACIONES con obra asignada, usa esa obra;
     * si no, usa la obra por defecto (OFICINA LUM).
     */
    private String resolverNombreObra(AusenciaDia ad, Map<Long, String> nombreObras) {
        if ("VACACIONES".equals(ad.tipo()) && ad.obraId() != null) {
            return nombreObras.getOrDefault(ad.obraId(), OBRA_LUM);
        }
        return OBRA_LUM;
    }

    // Construye el nombre formateado del operario: "APELLIDOS, Nombre"
    private String buildNombreOperario(perfil p) {
        String aps = p.getApellidos() != null ? p.getApellidos().toUpperCase() : "";
        String nom = p.getName()      != null ? p.getName()                    : "S/N";
        return aps.isEmpty() ? nom : aps + ", " + nom;
    }

    private String buildNombreOperario(contabilidad_detalle_dto d) {
        String aps = d.getApellidos() != null ? d.getApellidos().toUpperCase() : "";
        String nom = d.getNombre()    != null ? d.getNombre()                  : "S/N";
        return aps.isEmpty() ? nom : aps + ", " + nom;
    }

    // Construye la categoria profesional (o "No asignado" si no tiene)
    private String buildCategoria(String grupoProfesional) {
        return grupoProfesional != null ? grupoProfesional : "No asignado";
    }

    // ─── PROCESADO PRINCIPAL (7 pasos optimizados) ────────────────────────────

    /*
     * PROCESAR DATOS: Metodo interno que transforma los datos crudos de la BD
     * en el formato estructurado para el informe de contabilidad.
     *
     * Recibe:
     * - datos: lista de registros de partes desde la BD
     * - desde/hasta: periodo del informe
     * - obraIds: IDs de obras (null si es administracion)
     * - jefeId: UUID del jefe (null si es administracion)
     * - t0: timestamp inicial para medir rendimiento
     *
     * Devuelve: lista de mapas con la contabilidad procesada
     *
     * PASOS:
     * 0. Subordinados: carga los subordinados del jefe (solo si no es admin)
     * 1. Perfiles: carga todos los perfiles relevantes en un solo viaje
     * 1b. Obras: prepara mapa de nombres (diferido para evitar consulta innecesaria)
     * 2. Agrupar: organiza los datos por trabajador + obra
     * 3. Inyectar sin partes: anade trabajadores que no tienen partes registrados
     * 4. Ausencias: carga las ausencias del periodo
     * 5. Inyectar filas de ausencia: crea filas para los dias de ausencia
     * 6. Rellenar ausencias por dia: anade las marcas de ausencia a cada fila
     * 7. Convertir: transforma las filas internas al formato de salida
     */
    private List<Map<String, Object>> procesarDatos(
            List<contabilidad_detalle_dto> datos,
            LocalDate desde,
            LocalDate hasta,
            List<Long> obraIds,
            UUID jefeId,
            long t0) {

        boolean esAdministracion = (obraIds == null);

        // ── PASO 0: Subordinados del jefe ─────────────────────────────────────
        // Si es un jefe de obra, cargar sus subordinados (2 niveles abajo)
        Set<String> codigosPersonalPropio = new HashSet<>();
        Set<UUID>   idsPersonalPropio     = new HashSet<>();

        if (jefeId != null) {
            for (perfil p : perfilRepo.findSubordinadosDosNiveles(jefeId)) {
                if (p.getCodigo() != null) codigosPersonalPropio.add(p.getCodigo());
                idsPersonalPropio.add(p.getId());
            }
        }

        // ── PASO 1: Perfiles en un solo viaje a BD ────────────────────────────
        // Carga todos los perfiles que aparecen en los partes o son subordinados
        // del jefe, usando una consulta combinada optimizada
        Set<String> codigosEnDatos = new HashSet<>();
        for (contabilidad_detalle_dto d : datos) {
            if (d.getCodigo() != null) codigosEnDatos.add(d.getCodigo());
        }

        Collection<String> codigosParam = codigosEnDatos.isEmpty()
                ? List.of(CODIGO_CENTINELA) : codigosEnDatos;
        Collection<UUID> idsParam = idsPersonalPropio.isEmpty()
                ? List.of(UUID_CENTINELA)   : idsPersonalPropio;

        List<perfil> todosPerfiles = perfilRepo.findParaContabilidad(
                codigosParam, idsParam,
                List.of(user_rol.OPERARIO, user_rol.ENCARGADO),
                esAdministracion
        );

        // Indexar perfiles por codigo y por UUID para acceso rapido
        Map<String, perfil> codigoAPerfil = new HashMap<>(todosPerfiles.size() * 2);
        Map<UUID,   perfil> idAPerfil     = new HashMap<>(todosPerfiles.size() * 2);
        for (perfil p : todosPerfiles) {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
            if (p.getId()     != null) idAPerfil    .put(p.getId(),     p);
        }

        // ── PASO 1b: Nombres de obras (diferido) ──────────────────────────────
        // El nombre de obra ya viene en el DTO desde la BD, no hace falta
        // cargarlo ahora. Solo se cargara si hay ausencias con obra_id (paso 4).
        Map<Long, String> nombreObras = new HashMap<>();

        // ── PASO 2: Agrupar filas por trabajador + obra ───────────────────────
        Map<String, FilaContabilidad> mapaAgrupado = new LinkedHashMap<>();
        Set<String> codigosConParte = new HashSet<>();

        for (contabilidad_detalle_dto d : datos) {
            String nombreObraRaw   = d.getObra_nombre() != null ? d.getObra_nombre() : "Sin Obra";
            String especialidad    = d.getEspecialidad() != null
                    ? d.getEspecialidad().toUpperCase() : "";
            boolean esFont         = "FONTANERIA".equals(especialidad);
            String nombreObraVista = esFont ? "Font " + nombreObraRaw : nombreObraRaw;

            String codigoUser = d.getCodigo() != null ? d.getCodigo() : "000";
            String clave      = codigoUser + "|" + nombreObraVista;

            codigosConParte.add(codigoUser);

            FilaContabilidad fila = mapaAgrupado.computeIfAbsent(clave, k ->
                    new FilaContabilidad(
                            codigoUser, buildNombreOperario(d),
                            nombreObraVista, buildCategoria(d.getGrupo_profesional())));

            LocalDate fechaKey = d.getFecha();
            double    horas    = d.getHoras_totales() != null ? d.getHoras_totales() : 0.0;
            Long      parteId  = d.getParteId();

            if (fechaKey != null) {
                String fechaStr = fechaKey.toString();
                Map<String, Object> existing = fila.horasPorDia.get(fechaStr);
                if (existing != null) {
                    // Si ya hay horas para ese dia, acumular (varios partes en un mismo dia)
                    double acum = ((Number) existing.get("horas")).doubleValue() + horas;
                    existing.put("horas", acum);
                } else {
                    fila.horasPorDia.put(fechaStr, entradaDia(horas, parteId));
                }
            }
            fila.totalHoras += horas;
        }

        // ── PASO 3: Anadir trabajadores sin partes ────────────────────────────
        // Si es administracion o jefe de obra, incluir tambien los trabajadores
        // activos que no tienen ningun parte registrado en el periodo,
        // para que aparezcan en el informe con 0 horas.
        boolean inyectarSinPartes = esAdministracion || !codigosPersonalPropio.isEmpty();

        if (inyectarSinPartes) {
            for (perfil p : codigoAPerfil.values()) {
                if (p.getCodigo() == null)                                   continue;
                if (p.getRol() != user_rol.OPERARIO
                        && p.getRol() != user_rol.ENCARGADO)                 continue;
                if (!p.isActivo())                                           continue;
                if (codigosConParte.contains(p.getCodigo()))                 continue;
                if (!esAdministracion
                        && !codigosPersonalPropio.contains(p.getCodigo()))   continue;

                String claveSinParte = p.getCodigo() + "|__SIN_PARTE__";
                mapaAgrupado.putIfAbsent(claveSinParte,
                        new FilaContabilidad(
                                p.getCodigo(), buildNombreOperario(p),
                                "", buildCategoria(p.getGrupo_profesional())));
            }
        }

        // ── PASO 4: Cargar y procesar ausencias ───────────────────────────────
        List<Ausencia> ausenciasRango = ausenciaRepo.findEnRangoOptional(
                desde, hasta, idsPersonalPropio, esAdministracion);

        // Cargar nombres de obras solo para ausencias que referencian una obra
        Set<Long> obraIdsAusencias = ausenciasRango.stream()
                .map(Ausencia::getObraId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!obraIdsAusencias.isEmpty()) {
            obrasRepo.findAllById(obraIdsAusencias)
                    .forEach(o -> nombreObras.put(o.getId(), o.getNombre()));
        }

        // Indexar ausencias por trabajador y fecha para acceso rapido
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

        // ── PASO 5: Crear filas de ausencia en el resultado ───────────────────
        // Para cada trabajador con ausencias, crear filas en el informe
        // con la obra correspondiente (OFICINA LUM por defecto, o la obra asignada)
        Map<UUID, Set<String>> obrasVacacionesPorPerfil = new HashMap<>();

        if (esAdministracion || !codigosPersonalPropio.isEmpty()) {
            for (Map.Entry<UUID, Map<LocalDate, AusenciaDia>> entry
                    : ausenciasFecha.entrySet()) {

                UUID   perfilId = entry.getKey();
                perfil p        = idAPerfil.get(perfilId);
                if (p == null || p.getCodigo() == null) continue;

                Set<String> obrasNecesarias = entry.getValue().values().stream()
                        .map(ad -> resolverNombreObra(ad, nombreObras))
                        .collect(Collectors.toSet());

                Set<String> obrasVac = entry.getValue().values().stream()
                        .filter(ad -> "VACACIONES".equals(ad.tipo()) && ad.obraId() != null)
                        .map(ad -> nombreObras.getOrDefault(ad.obraId(), OBRA_LUM))
                        .collect(Collectors.toSet());
                if (!obrasVac.isEmpty()) {
                    obrasVacacionesPorPerfil.put(perfilId, obrasVac);
                }

                for (String nombreObra : obrasNecesarias) {
                    String claveObra = p.getCodigo() + "|" + nombreObra;
                    mapaAgrupado.computeIfAbsent(claveObra, k ->
                            new FilaContabilidad(
                                    p.getCodigo(), buildNombreOperario(p),
                                    nombreObra, buildCategoria(p.getGrupo_profesional())));
                }
                // Si el trabajador tenia una fila "sin parte", reemplazarla por la de ausencia
                mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
            }
        }

        // ── PASO 6: Anadir marcas de ausencia a cada fila ─────────────────────
        // Para cada fila del informe, si es una fila de ausencias, rellenar
        // los dias con la letra correspondiente (B=Baja, V=Vacaciones, P=Paternidad)
        for (FilaContabilidad fila : mapaAgrupado.values()) {
            String obraFila = fila.obra;
            perfil p        = codigoAPerfil.get(fila.codigo);
            if (p == null) continue;

            boolean esFilaAusencia = OBRA_LUM.equals(obraFila)
                    || obrasVacacionesPorPerfil
                    .getOrDefault(p.getId(), Collections.emptySet())
                    .contains(obraFila);

            if (!esFilaAusencia) continue;

            Map<LocalDate, AusenciaDia> ausFecha =
                    ausenciasFecha.getOrDefault(p.getId(), Collections.emptyMap());

            for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                AusenciaDia ad = ausFecha.get(dia);
                if (ad == null) continue;
                String obraEsperada = resolverNombreObra(ad, nombreObras);
                if (obraEsperada.equals(obraFila)) {
                    fila.ausenciasPorDia.put(dia.toString(), ad.tipo());
                }
            }
        }

        // ── PASO 7: Convertir al formato de salida ────────────────────────────
        List<Map<String, Object>> resultado = mapaAgrupado.values().stream()
                .map(FilaContabilidad::toMap)
                .collect(Collectors.toList());

        return resultado;
    }
}