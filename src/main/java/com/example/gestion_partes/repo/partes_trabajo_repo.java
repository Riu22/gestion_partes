package com.example.gestion_partes.repo;

import com.example.gestion_partes.dto.contabilidad_detalle_dto;
import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.especialidad;
import com.example.gestion_partes.model.partes_trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface partes_trabajo_repo extends JpaRepository<partes_trabajo, Long> {

    List<partes_trabajo> findByObraIdAndFecha(Long obraId, LocalDate fecha);
    List<partes_trabajo> findByObraId(Long obraId);
    List<partes_trabajo> findByPerfilId(UUID uuid);
    List<partes_trabajo> findByFecha(LocalDate fecha);

    // Encargado ve partes de sus operarios
    @Query("SELECT p FROM partes_trabajo p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_trabajo> findPartesParaEncargado(UUID encargadoId);

    // Jefe de obra ve partes de sus encargados y de los operarios de esos encargados
    @Query("SELECT p FROM partes_trabajo p WHERE " +
            "p.perfil.jefeDirecto.id = :jefeId OR " +
            "p.perfil.jefeDirecto.jefeDirecto.id = :jefeId")
    List<partes_trabajo> findPartesParaJefeObra(UUID jefeId);

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

    @Query("SELECT p.perfil.codigo as codigo, " +
            "p.perfil.name as nombre, " +
            "p.perfil.apellidos as apellidos, " +   // ← alias = apellidos (con s)
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

    @Query("""
    SELECT DISTINCT p FROM partes_trabajo p
    LEFT JOIN p.perfil pr
    LEFT JOIN pr.jefeDirecto jd1
    LEFT JOIN jd1.jefeDirecto jd2
    LEFT JOIN asignacion_obra a ON a.obra.id = p.obra.id AND a.perfil.id = :perfilId
    WHERE pr.id = :perfilId
       OR a.obra.id IS NOT NULL
       OR jd1.id = :perfilId
       OR jd2.id = :perfilId
""")
    List<partes_trabajo> findPartesVisiblesParaPerfil(@Param("perfilId") UUID perfilId);
    @Query("SELECT p FROM partes_trabajo p WHERE " +
            "p.obra.id IN :obraIds AND " +
            "(:operario IS NULL OR LOWER(p.perfil.name) LIKE LOWER(CONCAT('%', :operario, '%')) " +
            "OR LOWER(p.perfil.apellidos) LIKE LOWER(CONCAT('%', :operario, '%'))) AND " +
            "(:especialidad IS NULL OR p.especialidad = :especialidad)")
    List<partes_trabajo> buscarPartesPorObraIds(
            @Param("obraIds") List<Long> obraIds,
            @Param("operario") String operario,
            @Param("especialidad") String especialidad);
    @Query(value = "SELECT p.codigo as codigo, " +
            "p.nombre as nombre, " +
            "p.apellidos as apellidos, " +
            "p.grupo_profesional as grupo_profesional, " +
            "o.nombre as obra_nombre, " +
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

    @Query(value = "SELECT p.codigo as codigo, " +
            "p.nombre as nombre, " +
            "p.apellidos as apellidos, " +
            "p.grupo_profesional as grupo_profesional, " +
            "o.nombre as obra_nombre, " +
            "pt.fecha as fecha, " +
            "pt.especialidad as especialidad, " +
            "(pt.horas_normales + pt.horas_extra) as horas_totales " +
            "FROM partes_trabajo pt " +
            "JOIN perfiles p ON pt.usuario_id = p.id " +
            "JOIN obras o ON pt.point_obra_id = o.id " +
            "WHERE pt.fecha BETWEEN :desde AND :hasta " +
            "AND o.id IN :obraIds " +
            "AND o.activa = true " +
            "ORDER BY p.apellidos ASC, p.nombre ASC, o.nombre ASC, pt.fecha ASC",
            nativeQuery = true)
    List<contabilidad_detalle_dto> getDetalleContabilidadPorObras(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("obraIds") List<Long> obraIds
    );

    @Query("SELECT MIN(p.fecha) FROM partes_trabajo p")
    Optional<LocalDate> findFechaMasAntigua();

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
}