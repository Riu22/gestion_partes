/*
 * REPOSITORIO: partes_trabajo_repo (Acceso a base de datos de partes de trabajo)
 *
 * Proporciona todos los metodos para consultar y manipular la tabla
 * "partes_trabajo". Es el repositorio mas complejo porque los partes
 * se consultan de muchas formas distintas: por fechas, por obras,
 * por trabajadores, con filtros, con agrupaciones, etc.
 *
 * Metodos principales:
 *
 * Busquedas basicas:
 * - findByObraIdAndFecha: Partes de una obra en una fecha concreta
 * - findByObraId: Todos los partes de una obra
 * - findByPerfilId: Todos los partes de un trabajador
 * - findByFecha: Todos los partes de una fecha concreta
 *
 * Busquedas por jerarquia:
 * - findPartesParaEncargado: Partes de los operarios a cargo de un encargado
 * - findPartesParaJefeObra: Partes visibles para un jefe de obra (2 niveles)
 * - findPartesVisiblesParaPerfilDesde: Partes visibles segun el rol
 *
 * Busquedas con filtros:
 * - buscarPartes: Busca partes por nombre de obra, operario y especialidad
 * - buscarPartesPorObraIds: Busca partes filtrado por obras, operario y especialidad
 *
 * Informes y resumenes:
 * - getResumenQuincena: Resumen de horas por trabajador y obra en un periodo
 * - getDetalleContabilidad: Detalle completo para contabilidad
 * - getDetalleContabilidadPorObras: Detalle filtrando por obras y jerarquia
 * - findHorasPorPerfilYFecha: Horas totales agrupadas por trabajador y fecha
 *
 * Otros:
 * - findDistinctFechasByPerfilId: Fechas en las que un trabajador tiene partes
 * - findFechaMasAntigua: La fecha del parte mas antiguo del sistema
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.partes_trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface partes_trabajo_repo extends JpaRepository<partes_trabajo, Long> {

    // BUSQUEDAS BASICAS

    // Busca partes por obra y fecha (ej: todos los que trabajaron en "Obra X" el 01/06/2026)
    List<partes_trabajo> findByObraIdAndFecha(Long obraId, LocalDate fecha);

    // Todos los partes de una obra concreta
    List<partes_trabajo> findByObraId(Long obraId);

    // Todos los partes de un trabajador concreto
    List<partes_trabajo> findByPerfilId(UUID uuid);

    // Todos los partes de una fecha concreta (todos los trabajadores que registraron parte ese dia)
    List<partes_trabajo> findByFecha(LocalDate fecha);

    // Obtiene las fechas distintas en las que un trabajador tiene partes registrados
    @Query("SELECT DISTINCT p.fecha FROM partes_trabajo p WHERE p.perfil.id = :perfilId ORDER BY p.fecha ASC")
    List<LocalDate> findDistinctFechasByPerfilId(@Param("perfilId") UUID perfilId);

    // BUSQUEDAS POR JERARQUIA

    /*
     * Obtiene todos los partes de trabajo de los operarios que dependen
     * directamente de un encargado concreto.
     * Recibe: el ID del encargado
     * Devuelve: partes de los operarios a su cargo
     */
    @Query("SELECT p FROM partes_trabajo p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_trabajo> findPartesParaEncargado(UUID encargadoId);

    /*
     * Obtiene todos los partes visibles para un jefe de obra.
     * Incluye:
     * - Partes de los subordinados directos del jefe (encargados)
     * - Partes de los subordinados de los encargados (operarios)
     * Recibe: el ID del jefe de obra
     */
    @Query("SELECT p FROM partes_trabajo p WHERE " +
            "p.perfil.jefeDirecto.id = :jefeId OR " +
            "p.perfil.jefeDirecto.jefeDirecto.id = :jefeId")
    List<partes_trabajo> findPartesParaJefeObra(UUID jefeId);

    // BUSQUEDAS CON FILTROS

    /*
     * Busca partes de trabajo con filtros opcionales.
     * Permite buscar por:
     * - obra: nombre de la obra (busqueda parcial, no sensible a mayusculas)
     * - operario: nombre del trabajador (busqueda parcial)
     * - especialidad: tipo de trabajo (ELECTRICIDAD o FONTANERIA)
     * Si un filtro es null, se ignora y no filtra.
     */
    @Query(value = "SELECT p.* FROM public.partes_trabajo p " +
            "LEFT JOIN public.obras o ON p.point_obra_id = o.id " +
            "LEFT JOIN public.perfiles perf ON p.usuario_id = perf.id " +
            "WHERE (:obra IS NULL OR LOWER(CAST(o.nombre AS VARCHAR)) LIKE LOWER(CONCAT('%', CAST(:obra AS VARCHAR), '%'))) " +
            "AND (:operario IS NULL OR LOWER(CONCAT(perf.nombre, ' ', perf.apellidos)) LIKE LOWER(CONCAT('%', CAST(:operario AS VARCHAR), '%'))) " +
            "AND (:especialidad IS NULL OR CAST(p.especialidad AS VARCHAR) = CAST(:especialidad AS VARCHAR))",
            nativeQuery = true)
    List<partes_trabajo> buscarPartes(
            @Param("obra") String obra,
            @Param("operario") String operario,
            @Param("especialidad") String especialidad
    );

    // INFORMES Y RESUMENES

    /*
     * Genera el resumen de una quincena (periodo de 15 dias).
     * Agrupa las horas totales de cada trabajador en cada obra,
     * teniendo en cuenta la especialidad. Si la especialidad es
     * FONTANERIA, anade el prefijo "Font " al nombre de la obra
     * para distinguirla.
     *
     * Recibe: fecha de inicio y fin del periodo
     * Devuelve: lista de quincena_dto con codigo, nombre, apellidos,
     *           obra y total de horas
     */
    @Query("SELECT p.perfil.codigo as codigo, " +
            "p.perfil.name as nombre, " +
            "p.perfil.apellidos as apellidos, " +
            "CASE WHEN p.especialidad = 'FONTANERIA' THEN CONCAT('Font ', p.obra.nombre) " +
            "     ELSE p.obra.nombre END as obra, " +
            "SUM(p.horas_normales) as total_horas " +
            "FROM partes_trabajo p " +
            "WHERE p.fecha BETWEEN :desde AND :hasta " +
            "GROUP BY p.perfil.codigo, p.perfil.name, p.perfil.apellidos, p.obra.nombre, p.especialidad " +
            "ORDER BY p.perfil.apellidos, p.perfil.name, p.obra.nombre")
    List<quincena_dto> getResumenQuincena(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    /*
     * Busca partes de trabajo filtrando por una lista de obras,
     * con filtros opcionales de operario y especialidad.
     * Similar a buscarPartes pero restringido a un conjunto de obras.
     */
    @Query("SELECT p FROM partes_trabajo p WHERE " +
            "p.obra.id IN :obraIds AND " +
            "(:operario IS NULL OR LOWER(p.perfil.name) LIKE LOWER(CONCAT('%', :operario, '%')) " +
            "OR LOWER(p.perfil.apellidos) LIKE LOWER(CONCAT('%', :operario, '%'))) AND " +
            "(:especialidad IS NULL OR p.especialidad = :especialidad)")
    List<partes_trabajo> buscarPartesPorObraIds(
            @Param("obraIds") List<Long> obraIds,
            @Param("operario") String operario,
            @Param("especialidad") String especialidad);

    /*
     * Obtiene el detalle completo de contabilidad para un periodo.
     * Devuelve cada parte individual con: codigo trabajador, nombre,
     * apellidos, grupo profesional, obra, fecha, especialidad y
     * horas totales (normales + extra).
     * Ordenado por apellidos, nombre, obra y fecha.
     *
     * Es la consulta principal para generar los informes de contabilidad
     * en Excel.
     */
    @Query(value = "SELECT pt.id as parteId, " +
            "p.codigo as codigo, " +
            "p.nombre as nombre, " +
            "p.apellidos as apellidos, " +
            "p.grupo_profesional as grupo_profesional, " +
            "o.nombre as obra_nombre, " +
            "pt.point_obra_id as obraId, " +
            "pt.fecha as fecha, " +
            "pt.especialidad as especialidad, " +
            "(pt.horas_normales + pt.horas_extra) as horas_totales " +
            "FROM partes_trabajo pt " +
            "JOIN perfiles p ON pt.usuario_id = p.id " +
            "JOIN obras o ON pt.point_obra_id = o.id " +
            "WHERE pt.fecha BETWEEN :desde AND :hasta " +
            "ORDER BY p.apellidos ASC, p.nombre ASC, o.nombre ASC, pt.fecha ASC",
            nativeQuery = true)
    List<contabilidad_detalle_dto> getDetalleContabilidad(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    /*
     * Version del detalle de contabilidad para un jefe de obra.
     * Filtra por las obras que el jefe puede ver y por su jerarquia
     * (sus subordinados directos y los subordinados de sus encargados).
     *
     * Usa un LEFT JOIN para resolver el segundo nivel jerarquico
     * (subordinados de encargados) en lugar de subconsultas, lo que
     * mejora el rendimiento.
     *
     * Recibe: periodo (desde, hasta), obras del jefe y su ID
     * Devuelve: detalle contable filtrado
     */
    @Query(value = """
            SELECT pt.id            AS parteId,
                   p.codigo         AS codigo,
                   p.nombre         AS nombre,
                   p.apellidos      AS apellidos,
                   p.grupo_profesional AS grupo_profesional,
                   o.nombre         AS obra_nombre,
                   pt.point_obra_id AS obraId,
                   pt.fecha         AS fecha,
                   pt.especialidad  AS especialidad,
                   (pt.horas_normales + pt.horas_extra) AS horas_totales
            FROM partes_trabajo pt
            JOIN perfiles p  ON pt.usuario_id    = p.id
            JOIN obras    o  ON pt.point_obra_id = o.id
            LEFT JOIN perfiles nivel2
                   ON p.jefe_directo_id        = nivel2.id
                  AND nivel2.jefe_directo_id   = :jefeId
            WHERE pt.fecha BETWEEN :desde AND :hasta
              AND o.activa = true
              AND (
                  o.id                 IN :obraIds
                  OR p.jefe_directo_id  = :jefeId
                  OR nivel2.id         IS NOT NULL
              )
            ORDER BY p.apellidos ASC, p.nombre ASC, o.nombre ASC, pt.fecha ASC
            """, nativeQuery = true)
    List<contabilidad_detalle_dto> getDetalleContabilidadPorObras(
            @Param("desde")   LocalDate   desde,
            @Param("hasta")   LocalDate   hasta,
            @Param("obraIds") List<Long>  obraIds,
            @Param("jefeId")  UUID        jefeId
    );

    // OTROS

    // Obtiene la fecha mas antigua de todos los partes registrados
    @Query("SELECT MIN(p.fecha) FROM partes_trabajo p")
    Optional<LocalDate> findFechaMasAntigua();

    /*
     * Obtiene el total de horas normales por trabajador y por fecha
     * en un rango de fechas. Devuelve una lista de arrays porque
     * cada fila tiene datos de distinto tipo (UUID, LocalDate, Double).
     * Se usa para comprobar si un trabajador tiene el dia completo
     * (8 horas o mas).
     */
    @Query("""
    SELECT p.perfil.id, p.fecha, SUM(p.horas_normales)
    FROM partes_trabajo p
    WHERE p.fecha BETWEEN :inicio AND :fin
    GROUP BY p.perfil.id, p.fecha
    """)
    List<Object[]> findHorasPorPerfilYFecha(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin
    );

    // Obtiene los partes desde una fecha hacia atras, ordenados del mas reciente al mas antiguo
    List<partes_trabajo> findByFechaGreaterThanEqualOrderByFechaDesc(LocalDate desde);

    /*
     * Obtiene los partes que son visibles para un perfil segun su rol:
     * - Si es el propio perfil, ve sus partes
     * - Si esta asignado a la obra, ve los partes de esa obra
     * - Si es jefe directo (jd1), ve los partes de sus subordinados
     * - Si es jefe de jefe (jd2), ve los partes de los subordinados de segundo nivel
     */
    @Query("""
    SELECT DISTINCT p FROM partes_trabajo p
    LEFT JOIN p.perfil pr
    LEFT JOIN pr.jefeDirecto jd1
    LEFT JOIN jd1.jefeDirecto jd2
    LEFT JOIN asignacion_obra a ON a.obra.id = p.obra.id AND a.perfil.id = :perfilId
    WHERE p.fecha >= :desde
    AND (
        pr.id = :perfilId
        OR a.obra.id IS NOT NULL
        OR jd1.id = :perfilId
        OR jd2.id = :perfilId
    )
    """)
    List<partes_trabajo> findPartesVisiblesParaPerfilDesde(
            @Param("perfilId") UUID perfilId,
            @Param("desde") LocalDate desde
    );
}