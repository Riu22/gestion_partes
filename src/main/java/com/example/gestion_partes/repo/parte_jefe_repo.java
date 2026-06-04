/*
 * REPOSITORIO: parte_jefe_repo (Acceso a base de datos de partes de jefe)
 *
 * Proporciona metodos para consultar los reportes de los jefes de obra
 * y encargados.
 *
 * Metodos:
 * - findByPerfilId: Todos los reportes de un jefe/encargado concreto
 * - findAll: Todos los reportes de todos los jefes
 * - findPartesParaEncargado: Reportes de los operarios a cargo de un encargado
 * - findByPerfilIdAndMes: Reportes de un jefe en un mes concreto
 * - findAllByMes: Todos los reportes de todos los jefes en un mes
 * - findByFechaInicioBetween: Reportes cuyo inicio esta en un rango de fechas
 * - findByPerfilIdAndFechaInicioBetween: Reportes de un jefe en un rango de fechas
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_jefe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface parte_jefe_repo extends JpaRepository<partes_jefe, Long> {

    // Todos los reportes de un jefe/encargado concreto
    List<partes_jefe> findByPerfilId(UUID perfilId);

    // Todos los reportes de todos los jefes
    List<partes_jefe> findAll();

    /*
     * Obtiene los reportes de los partes_jefe creados por los operarios
     * que dependen de un encargado (aunque normalmente los partes_jefe
     * los crean los JEFE_DE_OBRA y ENCARGADO, no los OPERARIO).
     */
    @Query("SELECT p FROM partes_jefe p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_jefe> findPartesParaEncargado(@Param("encargadoId") UUID encargadoId);

    // Reportes de un jefe en un mes y anio concretos
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

    // Todos los reportes de todos los jefes en un mes y anio concretos
    @Query("""
        SELECT p FROM partes_jefe p
        WHERE EXTRACT(YEAR FROM p.fecha_inicio) = :anio
        AND EXTRACT(MONTH FROM p.fecha_inicio) = :mes
        """)
    List<partes_jefe> findAllByMes(
            @Param("anio") int anio,
            @Param("mes") int mes);

    // Reportes cuyo inicio esta entre dos fechas
    @Query("SELECT p FROM partes_jefe p WHERE p.fecha_inicio >= :desde AND p.fecha_inicio <= :hasta")
    List<partes_jefe> findByFechaInicioBetween(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    // Reportes de un jefe concreto cuyo inicio esta entre dos fechas
    @Query("SELECT p FROM partes_jefe p WHERE p.perfil.id = :perfilId AND p.fecha_inicio >= :desde AND p.fecha_inicio <= :hasta")
    List<partes_jefe> findByPerfilIdAndFechaInicioBetween(
            @Param("perfilId") UUID perfilId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}