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

    // ── Métodos existentes (sin tocar) ────────────────────────────────────────

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

    @Query("""
        SELECT a FROM Ausencia a
        WHERE a.fechaInicio <= :fin
        AND a.fechaFin >= :inicio
        """)
    List<Ausencia> findTodasEnRango(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin
    );

    List<Ausencia> findByPerfilIdOrderByFechaInicioDesc(UUID perfilId);

    // ── Nuevo método optimizado ───────────────────────────────────────────────

    /**
     * Versión unificada para administración y jefe de obra.
     *
     * Cuando sinFiltro = true (administración) devuelve todas las ausencias
     * del rango, igual que findTodasEnRango().
     *
     * Cuando sinFiltro = false (jefe de obra) aplica el IN en SQL,
     * eliminando el stream().filter() que antes se hacía en Java.
     *
     * Nota: los parámetros inicio/fin siguen la misma convención
     * que findTodasEnRango() para no introducir inconsistencias.
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