/*
 * REPOSITORIO: asignacion_obra_repo (Acceso a base de datos de asignaciones)
 *
 * Proporciona metodos para consultar las asignaciones de trabajadores
 * a obras y las relaciones jerarquicas entre ellos.
 *
 * Metodos:
 * - findByObraId: Todos los trabajadores asignados a una obra
 * - findByPerfilId: Todas las obras a las que esta asignado un trabajador
 * - existsByPerfilIdAndObraId: Comprueba si un trabajador ya esta asignado a una obra
 * - findObrasDeEncargadoDeOperario: Obras del encargado de un operario concreto
 *   (sirve para saber a que obras esta asignado el supervisor de un trabajador)
 * - findPerfilesByObraId: Todos los perfiles (trabajadores) asignados a una obra
 * - findObraIdsByPerfilId: Solo los IDs de las obras de un trabajador
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.asignacion_obra;
import com.example.gestion_partes.model.perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface asignacion_obra_repo extends JpaRepository<asignacion_obra, Long> {
    // Todas las asignaciones de una obra (quienes trabajan ahi)
    List<asignacion_obra> findByObraId(Long obraId);

    // Todas las asignaciones de un trabajador (obras donde trabaja)
    List<asignacion_obra> findByPerfilId(UUID perfilId);

    // Comprueba si un trabajador ya esta asignado a una obra
    boolean existsByPerfilIdAndObraId(UUID perfilId, Long obraId);

    /*
     * Obtiene las obras del encargado al que reporta un operario.
     * Primero busca quien es el jefe directo del operario,
     * luego busca las obras asignadas a ese jefe.
     * Recibe: ID del operario
     * Devuelve: asignaciones del encargado de ese operario
     */
    @Query("SELECT ao FROM asignacion_obra ao WHERE ao.perfil.id = " +
            "(SELECT p.jefeDirecto.id FROM perfil p WHERE p.id = :operarioId)")
    List<asignacion_obra> findObrasDeEncargadoDeOperario(UUID operarioId);

    // Obtiene todos los perfiles (trabajadores) asignados a una obra
    @Query("SELECT ao.perfil FROM asignacion_obra ao WHERE ao.obra.id = :obraId")
    List<perfil> findPerfilesByObraId(@Param("obraId") Long obraId);

    // Obtiene solo los IDs de las obras a las que esta asignado un trabajador
    @Query("SELECT a.obra.id FROM asignacion_obra a WHERE a.perfil.id = :perfilId")
    List<Long> findObraIdsByPerfilId(@Param("perfilId") UUID perfilId);
}