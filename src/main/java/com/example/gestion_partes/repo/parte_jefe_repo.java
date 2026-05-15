package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_jefe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface parte_jefe_repo extends JpaRepository<partes_jefe, Long> {

    List<partes_jefe> findByPerfilId(UUID perfilId);

    List<partes_jefe> findAll();

    @Query("SELECT p FROM partes_jefe p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_jefe> findPartesParaEncargado(@Param("encargadoId") UUID encargadoId);

    @Query("""
        SELECT p FROM partes_jefe p
        WHERE p.perfil.id = :perfilId
        AND EXTRACT(YEAR FROM p.fecha_inicio) = :anio
        AND EXTRACT(MONTH FROM p.fecha_inicio) = :mes
        """)
    List<partes_jefe> findByPerfilIdAndMes(
            @Param("perfilId") UUID perfilId,
            @Param("anio") int anio,
            @Param("mes") int mes);

    @Query("""
        SELECT p FROM partes_jefe p
        WHERE EXTRACT(YEAR FROM p.fecha_inicio) = :anio
        AND EXTRACT(MONTH FROM p.fecha_inicio) = :mes
        """)
    List<partes_jefe> findAllByMes(
            @Param("anio") int anio,
            @Param("mes") int mes);

    @Query("SELECT p FROM partes_jefe p WHERE p.fecha_inicio >= :desde AND p.fecha_inicio <= :hasta")
    List<partes_jefe> findByFechaInicioBetween(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    @Query("SELECT p FROM partes_jefe p WHERE p.perfil.id = :perfilId AND p.fecha_inicio >= :desde AND p.fecha_inicio <= :hasta")
    List<partes_jefe> findByPerfilIdAndFechaInicioBetween(
            @Param("perfilId") UUID perfilId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}