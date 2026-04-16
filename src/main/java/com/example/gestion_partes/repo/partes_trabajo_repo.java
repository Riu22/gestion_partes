package com.example.gestion_partes.repo;

import com.example.gestion_partes.dto.quincena_dto;
import com.example.gestion_partes.model.partes_trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
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
            "AND (:operario IS NULL OR LOWER(CAST(perf.nombre_completo AS VARCHAR)) LIKE LOWER(CONCAT('%', CAST(:operario AS VARCHAR), '%'))) " +
            "AND (:especialidad IS NULL OR CAST(p.especialidad AS VARCHAR) = CAST(:especialidad AS VARCHAR))",
            nativeQuery = true)
    List<partes_trabajo> buscarPartes(
            @Param("obra") String obra,
            @Param("operario") String operario,
            @Param("especialidad") String especialidad
    );

    @Query("SELECT p.perfil.codigo as codigo, " +
            "p.perfil.name as nombre, " +
            "CASE WHEN p.especialidad = 'FONTANERIA' THEN CONCAT('Font ', p.obra.nombre) " +
            "     ELSE p.obra.nombre END as obra, " +
            "SUM(p.horas_normales) as total_horas " +
            "FROM partes_trabajo p " +
            "WHERE p.fecha BETWEEN :desde AND :hasta " +
            "GROUP BY p.perfil.codigo, p.perfil.name, p.obra.nombre, p.especialidad " +
            "ORDER BY p.perfil.name, p.obra.nombre")
    List<quincena_dto> getResumenQuincena(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );
}