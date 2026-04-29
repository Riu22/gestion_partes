package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface obra_repo extends JpaRepository<obra,Long> {
    List<obra> findAllByOrderByNombreAsc();
}
