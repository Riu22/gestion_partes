/*
 * REPOSITORIO: AusenciaRepo (Acceso a base de datos de ausencias)
 *
 * Proporciona metodos para consultar las ausencias laborales
 * (bajas, vacaciones, permisos de paternidad).
 *
 * Metodos:
 * - findSolapadasEnRango: Busca ausencias de un trabajador concreto
 *   que se solapen con un periodo de fechas (sirve para saber si
 *   un trabajador estaba de baja cuando deberia haber trabajado)
 * - findTodasEnRango: Todas las ausencias de cualquier trabajador
 *   en un periodo (para administracion)
 * - findByPerfilIdOrderByFechaInicioDesc: Historial de ausencias
 *   de un trabajador, ordenado de mas reciente a mas antiguo
 * - findEnRangoOptional: Version optimizada que permite filtrar
 *   por una lista de trabajadores o devolver todas (sin filtro)
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.Ausencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AusenciaRepo extends JpaRepository<Ausencia, Long> {

    /*
     * Busca ausencias de un trabajador concreto que se solapen
     * con un rango de fechas.
     *
     * "Se solapen" significa que la ausencia empieza antes o durante
     * el periodo y termina despues o durante el periodo.
     *
     * Recibe: el ID del trabajador, la fecha de inicio y fin del rango
     * Devuelve: lista de ausencias que coinciden
     */
    @Query("""
        SELECT a FROM Ausencia a
        WHERE a.perfilId = :perfilId
        AND a.fechaInicio <= :fin
        AND a.fechaFin >= :inicio
        """)
    List<Ausencia> findSolapadasEnRango(
            @Param("perfilId") UUID perfilId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin
    );

    /*
     * Busca TODAS las ausencias de cualquier trabajador en un rango
     * de fechas. Se usa para los informes de administracion.
     */
    @Query("""
        SELECT a FROM Ausencia a
        WHERE a.fechaInicio <= :fin
        AND a.fechaFin >= :inicio
        """)
    List<Ausencia> findTodasEnRango(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin
    );

    // Historial completo de ausencias de un trabajador,
    // ordenado de la mas reciente a la mas antigua
    List<Ausencia> findByPerfilIdOrderByFechaInicioDesc(UUID perfilId);

    /*
     * Version unificada y optimizada para buscar ausencias en un rango.
     *
     * Cuando sinFiltro = true: devuelve todas las ausencias del periodo
     * (sin filtrar por trabajadores). Es el equivalente a findTodasEnRango.
     *
     * Cuando sinFiltro = false: filtra por los trabajadores indicados
     * en la lista perfilIds. Sirve para que un jefe de obra vea solo
     * las ausencias de sus subordinados.
     *
     * Recibe: inicio y fin del periodo, lista de IDs para filtrar,
     *         y un booleano que indica si se filtra o no
     */
    @Query("""
        SELECT a FROM Ausencia a
        WHERE a.fechaInicio <= :fin
          AND a.fechaFin >= :inicio
          AND (:sinFiltro = true OR a.perfilId IN :perfilIds)
        """)
    List<Ausencia> findEnRangoOptional(
            @Param("inicio")     LocalDate        inicio,
            @Param("fin")        LocalDate        fin,
            @Param("perfilIds")  Collection<UUID> perfilIds,
            @Param("sinFiltro")  boolean          sinFiltro
    );
}