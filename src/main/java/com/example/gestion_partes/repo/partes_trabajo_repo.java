package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface partes_trabajo_repo extends JpaRepository<partes_trabajo, Long> {

    List<partes_trabajo> findByObraIdAndFecha(Long obraId, LocalDate fecha);

    List<partes_trabajo> findByObraId(Long obraId);

    List<partes_trabajo> findByPerfilId(UUID uuid);

    List<partes_trabajo> findByFecha(LocalDate fecha);

    @Query("SELECT p FROM partes_trabajo p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_trabajo> findPartesParaEncargado(UUID encargadoId);

    @Query("SELECT p FROM partes_trabajo p WHERE p.obra.id IN " +
            "(SELECT ao.obra.id FROM asignacion_obra ao WHERE ao.perfil.id = :jefeId)")
    List<partes_trabajo> findPartesParaJefeObra(UUID jefeId);
}