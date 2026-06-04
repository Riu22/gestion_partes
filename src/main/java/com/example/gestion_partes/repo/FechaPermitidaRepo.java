/*
 * REPOSITORIO: FechaPermitidaRepo (Acceso a base de datos de fechas habilitadas)
 *
 * Proporciona metodos para gestionar las fechas en las que se permite
 * a los trabajadores editar o crear partes de trabajo de forma retroactiva.
 *
 * Metodos:
 * - existsByPerfilIdAndFecha: Comprueba si un trabajador tiene habilitada
 *   una fecha concreta para editar
 * - findByPerfilIdOrderByFechaAsc: Obtiene todas las fechas habilitadas
 *   para un trabajador, ordenadas por fecha
 * - deleteByPerfilIdAndFecha: Elimina la habilitacion de una fecha concreta
 * - deleteByPerfilId: Elimina todas las habilitaciones de un trabajador
 * - findAllByOrderByPerfilIdAscFechaAsc: Todas las habilitaciones ordenadas
 * - eliminarFechasConParteCompleto: Tarea de limpieza automatica que borra
 *   las habilitaciones de fechas pasadas donde el trabajador ya tiene
 *   un parte completo (8+ horas)
 */
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

    // Comprueba si un trabajador tiene permiso para editar una fecha concreta
    boolean existsByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    // Obtiene todas las fechas habilitadas para un trabajador, ordenadas
    List<FechaPermitida> findByPerfilIdOrderByFechaAsc(UUID perfilId);

    // Elimina el permiso para una fecha concreta de un trabajador
    void deleteByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    // Elimina todos los permisos de un trabajador
    void deleteByPerfilId(UUID perfilId);

    // Todas las configuraciones de fechas habilitadas, ordenadas
    List<FechaPermitida> findAllByOrderByPerfilIdAscFechaAsc();

    /*
     * Tarea de limpieza automatica (ejecutada por el scheduler nocturno).
     * Elimina las fechas habilitadas que ya no son necesarias porque:
     * 1. La fecha ya ha pasado (fp.fecha < CURRENT_DATE)
     * 2. El trabajador ya tiene un parte completo (8+ horas) para esa fecha
     *
     * Esto evita acumular registros innecesarios en la tabla.
     * Devuelve: el numero de registros eliminados
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
    DELETE FROM fechas_permitidas fp
    WHERE EXISTS (
        SELECT 1 FROM partes_trabajo pt
        WHERE pt.usuario_id = fp.perfil_id
          AND pt.fecha = fp.fecha
        GROUP BY pt.usuario_id, pt.fecha
        HAVING SUM(COALESCE(pt.horas_normales, 0) + COALESCE(pt.horas_extra, 0)) >= 8
    )
    AND fp.fecha < CURRENT_DATE
    """, nativeQuery = true)
    int eliminarFechasConParteCompleto();
}