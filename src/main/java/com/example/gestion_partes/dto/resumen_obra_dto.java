/*
 * DTO: resumen_obra_dto (Resumen de una obra en el informe mensual)
 *
 * Define los datos de una obra dentro del resumen mensual del jefe.
 * Muestra las horas totales dedicadas a esa obra en el mes,
 * desglosadas por especialidad y con sus porcentajes.
 *
 * Campos que devuelve:
 * - nombre_obra:         Nombre de la obra
 * - codigo_obra:         Codigo interno de la obra
 * - horas_electricas:    Total de horas electricas en el mes
 * - horas_mecanicas:     Total de horas de fontaneria en el mes
 * - porcentaje_electrico: Porcentaje de horas electricas sobre el total
 * - porcentaje_mecanico:  Porcentaje de horas de fontaneria sobre el total
 */
package com.example.gestion_partes.dto;

public record resumen_obra_dto(
        String nombre_obra,
        String codigo_obra,
        Double horas_electricas,
        Double horas_mecanicas,
        Double porcentaje_electrico,
        Double porcentaje_mecanico
) {}