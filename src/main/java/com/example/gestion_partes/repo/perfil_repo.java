package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface perfil_repo extends JpaRepository<perfil,Long> {
    Optional<perfil> findByEmail(String email);
}
