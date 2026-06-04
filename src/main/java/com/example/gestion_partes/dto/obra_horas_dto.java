/*
 * DTO: obra_horas_dto (Horas por obra para el parte de jefe)
 *
 * Define la estructura para enviar las horas dedicadas a una obra
 * concreta dentro de un parte de jefe/encargado. Se usa como parte
 * de la lista "obras" en partes_jefe_dto.
 *
 * Campos:
 * - id_obra:          Identificador de la obra
 * - horas_electricas: Horas dedicadas a trabajos electricos
 * - horas_mecanicas:  Horas dedicadas a trabajos de fontaneria
 */
package com.example.gestion_partes.dto;

import java.util.UUID;

public record obra_horas_dto(
        Long id_obra,
        Double horas_electricas,
        Double horas_mecanicas
) {}