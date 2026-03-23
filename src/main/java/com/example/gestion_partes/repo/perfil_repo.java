package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface perfil_repo extends JpaRepository<perfil, UUID> {
    Optional<perfil> findByEmail(String email);
}
