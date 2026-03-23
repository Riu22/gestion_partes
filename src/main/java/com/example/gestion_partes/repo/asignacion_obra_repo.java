package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.asignacion_obra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface asignacion_obra_repo extends JpaRepository<asignacion_obra, Long> {
    List<asignacion_obra> findByObraId(Long obraId);
    List<asignacion_obra> findByPerfilId(UUID perfilId);
    boolean existsByPerfilIdAndObraId(UUID perfilId, Long obraId);
}