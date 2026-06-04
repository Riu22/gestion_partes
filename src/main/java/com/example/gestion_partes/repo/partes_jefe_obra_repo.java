/*
 * REPOSITORIO: partes_jefe_obra_repo (Acceso a detalle de partes de jefe)
 *
 * Proporciona metodos para consultar las lineas de detalle de los
 * reportes de jefes de obra (cada linea es una obra con sus horas).
 *
 * Metodos:
 * - findByParteJefeId: Obtiene todas las lineas de detalle de un
 *   parte de jefe concreto
 * - deleteByParteJefeId: Elimina todas las lineas de un parte de jefe
 *   (se usa al modificar o eliminar el parte completo)
 */
package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.partes_jefe_obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface partes_jefe_obra_repo extends JpaRepository<partes_jefe_obra, Long> {
    // Obtiene las lineas de detalle de un parte de jefe concreto
    List<partes_jefe_obra> findByParteJefeId(Long parteJefeId);

    // Elimina todas las lineas de un parte de jefe (por ejemplo, al borrar el parte)
    void deleteByParteJefeId(Long parteId);
}