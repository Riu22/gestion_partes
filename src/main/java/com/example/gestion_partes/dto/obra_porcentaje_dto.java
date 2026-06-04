/*
 * DTO: obra_porcentaje_dto (Porcentaje de dedicacion a una obra)
 *
 * Define la estructura para asignar un porcentaje de tiempo de
 * un trabajador a una obra concreta. Se usa para distribuir
 * el tiempo de los jefes de obra entre varias obras.
 *
 * El porcentaje debe estar entre 1 y 100.
 *
 * Campos:
 * - id_obra:    Identificador de la obra
 * - porcentaje: Porcentaje de tiempo dedicado (1 a 100)
 *
 * Ejemplo: Si un jefe de obra reparte su tiempo al 50% entre
 * dos obras, se enviarian dos obra_porcentaje_dto con porcentaje=50
 */
package com.example.gestion_partes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record obra_porcentaje_dto(
        @NotNull Long id_obra,
        @NotNull @Min(1) @Max(100) Double porcentaje
) {}