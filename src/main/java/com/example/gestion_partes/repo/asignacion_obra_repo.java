package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.asignacion_obra;
import com.example.gestion_partes.model.perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface asignacion_obra_repo extends JpaRepository<asignacion_obra, Long> {
    List<asignacion_obra> findByObraId(Long obraId);
    List<asignacion_obra> findByPerfilId(UUID perfilId);
    boolean existsByPerfilIdAndObraId(UUID perfilId, Long obraId);
    @Query("SELECT ao FROM asignacion_obra ao WHERE ao.perfil.id = " +
            "(SELECT p.jefeDirecto.id FROM perfil p WHERE p.id = :operarioId)")
    List<asignacion_obra> findObrasDeEncargadoDeOperario(UUID operarioId);

    @Query("SELECT ao.perfil FROM asignacion_obra ao WHERE ao.obra.id = :obraId")
    List<perfil> findPerfilesByObraId(@Param("obraId") Long obraId);}