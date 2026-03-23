package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface partes_trabajo_repo extends JpaRepository<partes_trabajo,Long> {
    List<partes_trabajo> findByObraIdAndFecha(Long aLong, LocalDate fecha);

    List<partes_trabajo> findByObraId(Long aLong);

    List<partes_trabajo> findByPerfilId(UUID uuid);

    List<partes_trabajo> findByFecha(LocalDate fecha);

    @Query("SELECT p FROM partes_trabajo p WHERE p.perfil.id IN " +
            "(SELECT a.operario.id FROM asignacion_encargado a WHERE a.encargado.id = :encargadoId)")
    List<partes_trabajo> findPartesParaEncargado(UUID encargadoId);

    @Query("SELECT p FROM partes_trabajo p " +
            "LEFT JOIN p.perfil.jefeDirecto jefe " +
            "LEFT JOIN jefe.jefeDirecto abuelo " +
            "WHERE jefe.id = :jefeId OR abuelo.id = :jefeId")
    List<partes_trabajo> findPartesParaJefeObra(UUID jefeId);
}
