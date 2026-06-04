/*
 * REPOSITORIO: obra_repo (Acceso a base de datos de obras)
 *
 * Proporciona los metodos para consultar la tabla "obras".
 *
 * Metodos:
 * - findAllByOrderByNombreAsc: Obtiene todas las obras ordenadas
 *   alfabeticamente por nombre
 * - findByActivaTrueOrderByNombreAsc: Obtiene solo las obras que estan
 *   activas (en ejecucion), ordenadas alfabeticamente por nombre
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface obra_repo extends JpaRepository<obra,Long> {
    List<obra> findAllByOrderByNombreAsc();
    List<obra> findByActivaTrueOrderByNombreAsc();
}
