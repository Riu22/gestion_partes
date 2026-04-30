package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.perfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface perfil_repo extends JpaRepository<perfil, UUID> {
    List<perfil> findAllByOrderByActivoDescApellidosAscNameAsc();
    List<perfil> findByJefeDirecto_Id(UUID jefeId);
}
