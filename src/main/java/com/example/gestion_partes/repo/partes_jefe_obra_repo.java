package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_jefe_obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface partes_jefe_obra_repo extends JpaRepository<partes_jefe_obra, Long> {
    List<partes_jefe_obra> findByParteJefeId(Long parteJefeId);
}