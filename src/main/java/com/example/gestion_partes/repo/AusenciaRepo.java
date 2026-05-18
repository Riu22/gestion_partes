package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.Ausencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AusenciaRepo extends JpaRepository<Ausencia, Long> {

    // Ausencias activas en un rango de fechas para un perfil
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

    // Todas las ausencias que se solapan con un rango (para todos los perfiles)
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
}
