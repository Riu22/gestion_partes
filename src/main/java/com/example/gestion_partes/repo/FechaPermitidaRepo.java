package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.FechaPermitida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FechaPermitidaRepo extends JpaRepository<FechaPermitida, Long> {

    boolean existsByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    List<FechaPermitida> findByPerfilIdOrderByFechaAsc(UUID perfilId);

    void deleteByPerfilIdAndFecha(UUID perfilId, LocalDate fecha);

    void deleteByPerfilId(UUID perfilId);

    List<FechaPermitida> findAllByOrderByPerfilIdAscFechaAsc();
}
