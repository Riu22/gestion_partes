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

    private static final String OBRA_LUM       = "OFICINA LUM/ALMACÉN LUM";
    private static final String BASE_URL_PARTE = "/partes/";

    // Centinelas para evitar IN () vacío en SQL cuando las colecciones están vacías.
    private static final String       CODIGO_CENTINELA = "__VACIO__";
    private static final UUID         UUID_CENTINELA   =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

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

    // ── API pública ───────────────────────────────────────────────────────────

    public List<quincena_dto> getResumenQuincena(LocalDate desde, LocalDate hasta) {
        return partesRepo.getResumenQuincena(desde, hasta);
    }

    public List<Map<String, Object>> getDetalleContabilidad(
            LocalDate desde, LocalDate hasta) {
        long t0 = System.currentTimeMillis();
        List<contabilidad_detalle_dto> datos = partesRepo.getDetalleContabilidad(desde, hasta);
        System.out.printf("[PERF] query principal:     %4d ms  (%d filas)%n",
                System.currentTimeMillis() - t0, datos.size());
        return procesarDatos(datos, desde, hasta, null, null, t0);
    }

    public List<Map<String, Object>> getDetalleContabilidadPorObras(
            LocalDate desde, LocalDate hasta, List<Long> obraIds, UUID jefeId) {
        long t0 = System.currentTimeMillis();
        List<contabilidad_detalle_dto> datos =
                partesRepo.getDetalleContabilidadPorObras(desde, hasta, obraIds, jefeId);
        System.out.printf("[PERF] query principal:     %4d ms  (%d filas)%n",
                System.currentTimeMillis() - t0, datos.size());
        return procesarDatos(datos, desde, hasta, obraIds, jefeId, t0);
    }

    // ── Helpers de fecha ──────────────────────────────────────────────────────

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

    private String buildCategoria(String grupoProfesional) {
        return grupoProfesional != null ? grupoProfesional : "No asignado";
    }

    // ── Procesado principal ───────────────────────────────────────────────────

    private List<Map<String, Object>> procesarDatos(
            List<contabilidad_detalle_dto> datos,
            LocalDate desde,
            LocalDate hasta,
            List<Long> obraIds,
            UUID jefeId,
            long t0) {

        boolean esAdministracion = (obraIds == null);

        // ── 0. Subordinados ───────────────────────────────────────────────────
        Set<String> codigosPersonalPropio = new HashSet<>();
        Set<UUID>   idsPersonalPropio     = new HashSet<>();

        if (jefeId != null) {
            for (perfil p : perfilRepo.findSubordinadosDosNiveles(jefeId)) {
                if (p.getCodigo() != null) codigosPersonalPropio.add(p.getCodigo());
                idsPersonalPropio.add(p.getId());
            }
        }
        System.out.printf("[PERF] paso 0 subordinados: %4d ms%n",
                System.currentTimeMillis() - t0);

        // ── 1. Perfiles — un solo round-trip ──────────────────────────────────
        //
        // findParaContabilidad() fusiona los tres casos anteriores:
        //   a) perfiles que aparecen en partes (por código)
        //   b) subordinados del jefe (por UUID)
        //   c) todos los OPERARIO/ENCARGADO activos (solo administración)
        //
        // Se usan centinelas cuando las colecciones están vacías para evitar
        // que Hibernate genere "IN ()" inválido en SQL.

        Set<String> codigosEnDatos = new HashSet<>();
        for (contabilidad_detalle_dto d : datos) {
            if (d.getCodigo() != null) codigosEnDatos.add(d.getCodigo());
        }

        Collection<String> codigosParam = codigosEnDatos.isEmpty()
                ? List.of(CODIGO_CENTINELA) : codigosEnDatos;
        Collection<UUID> idsParam = idsPersonalPropio.isEmpty()
                ? List.of(UUID_CENTINELA)   : idsPersonalPropio;

        List<perfil> todosPerfiles = perfilRepo.findParaContabilidad(
                codigosParam,
                idsParam,
                List.of(user_rol.OPERARIO, user_rol.ENCARGADO),
                esAdministracion
        );

        int cap = todosPerfiles.size() * 2;
        Map<String, perfil> codigoAPerfil = new HashMap<>(cap);
        Map<UUID,   perfil> idAPerfil     = new HashMap<>(cap);
        for (perfil p : todosPerfiles) {
            if (p.getCodigo() != null) codigoAPerfil.put(p.getCodigo(), p);
            if (p.getId()     != null) idAPerfil    .put(p.getId(),     p);
        }
        System.out.printf("[PERF] paso 1 perfiles:     %4d ms  (%d perfiles)%n",
                System.currentTimeMillis() - t0, todosPerfiles.size());

        // ── 1b. Obras ─────────────────────────────────────────────────────────
        //
        // Ya NO se precarga el mapa de obras desde los partes: obra_nombre
        // llega directamente en el DTO via JOIN en la query principal.
        //
        // El mapa se rellena más adelante (paso 4) solo con las obras
        // referenciadas en ausencias que tengan obra_id no nulo.
        // Esto elimina el round-trip anterior de ~36 ms.
        Map<Long, String> nombreObras = new HashMap<>();
        System.out.printf("[PERF] paso 1b obras:       %4d ms  (diferido al paso 4)%n",
                System.currentTimeMillis() - t0);

        // ── 2. Agrupar filas ──────────────────────────────────────────────────
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
                            codigoUser,
                            buildNombreOperario(d),
                            nombreObraVista,
                            buildCategoria(d.getGrupo_profesional())));

            LocalDate fechaKey = d.getFecha();
            double    horas    = d.getHoras_totales() != null ? d.getHoras_totales() : 0.0;
            Long      parteId  = d.getParteId();

            if (fechaKey != null) {
                String fechaStr = fechaKey.toString();
                Map<String, Object> existing = fila.horasPorDia.get(fechaStr);
                if (existing != null) {
                    double acum = ((Number) existing.get("horas")).doubleValue() + horas;
                    existing.put("horas", acum);
                } else {
                    fila.horasPorDia.put(fechaStr, entradaDia(horas, parteId));
                }
            }
            fila.totalHoras += horas;
        }
        System.out.printf("[PERF] paso 2 agrupado:     %4d ms  (%d filas agrupadas)%n",
                System.currentTimeMillis() - t0, mapaAgrupado.size());

        // ── 3. Inyectar sin partes ────────────────────────────────────────────
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
                                p.getCodigo(),
                                buildNombreOperario(p),
                                "",
                                buildCategoria(p.getGrupo_profesional())));
            }
        }
        System.out.printf("[PERF] paso 3 sin partes:   %4d ms%n",
                System.currentTimeMillis() - t0);

        // ── 4. Ausencias ──────────────────────────────────────────────────────
        List<Ausencia> ausenciasRango = ausenciaRepo.findEnRangoOptional(
                desde, hasta, idsPersonalPropio, esAdministracion);
        System.out.printf("[PERF] paso 4a ausencias BD:%4d ms  (%d ausencias)%n",
                System.currentTimeMillis() - t0, ausenciasRango.size());

        // Carga obras solo para las ausencias que referencian una obra_id.
        // Sustituye el paso 1b anterior, con la ventaja de que en quincenas
        // sin ausencias con obra no se hace ningún round-trip adicional.
        Set<Long> obraIdsAusencias = ausenciasRango.stream()
                .map(Ausencia::getObraId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!obraIdsAusencias.isEmpty()) {
            obrasRepo.findAllById(obraIdsAusencias)
                    .forEach(o -> nombreObras.put(o.getId(), o.getNombre()));
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
        System.out.printf("[PERF] paso 4b ausencias idx:%4d ms%n",
                System.currentTimeMillis() - t0);

        // ── 5. Inyectar filas de ausencia ─────────────────────────────────────
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
                                    p.getCodigo(),
                                    buildNombreOperario(p),
                                    nombreObra,
                                    buildCategoria(p.getGrupo_profesional())));
                }
                mapaAgrupado.remove(p.getCodigo() + "|__SIN_PARTE__");
            }
        }
        System.out.printf("[PERF] paso 5 filas aus.:   %4d ms%n",
                System.currentTimeMillis() - t0);

        // ── 6. Rellenar ausencias_por_dia ─────────────────────────────────────
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
        System.out.printf("[PERF] paso 6 aus. por dia: %4d ms%n",
                System.currentTimeMillis() - t0);

        // ── 7. Convertir ──────────────────────────────────────────────────────
        List<Map<String, Object>> resultado = mapaAgrupado.values().stream()
                .map(FilaContabilidad::toMap)
                .collect(Collectors.toList());
        System.out.printf("[PERF] paso 7 convertir:    %4d ms  (%d filas resultado)%n",
                System.currentTimeMillis() - t0, resultado.size());
        System.out.printf("[PERF] ── total procesarDatos: %d ms ──%n",
                System.currentTimeMillis() - t0);

        return resultado;
    }
}