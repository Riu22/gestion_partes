package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.FechaPermitida;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FechaPermitidaRepo extends JpaRepository<FechaPermitida, Long> {

    boolean existsByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    List<FechaPermitida> findByPerfilIdOrderByFechaAsc(UUID perfilId);

    void deleteByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    void deleteByPerfilId(UUID perfilId);

    List<FechaPermitida> findAllByOrderByPerfilIdAscFechaAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
    DELETE FROM fechas_permitidas fp
    WHERE EXISTS (
        SELECT 1 FROM partes_trabajo pt
        WHERE pt.usuario_id = fp.perfil_id
          AND pt.fecha = fp.fecha
          AND (pt.horas_normales + pt.horas_extra) >= 8
    )
    AND fp.fecha < CURRENT_DATE
    """, nativeQuery = true)
    int eliminarFechasConParteCompleto();
}