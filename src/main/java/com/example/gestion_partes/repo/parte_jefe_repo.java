package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_jefe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface parte_jefe_repo extends JpaRepository<partes_jefe, Long> {

    // Partes propios del jefe
    List<partes_jefe> findByPerfilId(UUID perfilId);

    // GESTION/ADMIN ven todos
    List<partes_jefe> findAll();

    // Encargado ve partes de sus jefes directos
    @Query("SELECT p FROM partes_jefe p WHERE p.perfil.jefeDirecto.id = :encargadoId")
    List<partes_jefe> findPartesParaEncargado(UUID encargadoId);
}