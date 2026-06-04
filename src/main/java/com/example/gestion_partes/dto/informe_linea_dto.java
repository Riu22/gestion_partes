/*
 * DTO: informe_linea_dto (Linea de detalle de un informe de jefe)
 *
 * Define una linea individual dentro del informe de un jefe/encargado.
 * Cada linea representa una obra con sus horas desglosadas por
 * especialidad y los porcentajes correspondientes.
 *
 * Ejemplo de valores:
 *   nombre_obra = "Hotel Miramar"
 *   horas_electricas = 40.0
 *   horas_mecanicas = 20.0
 *   porcentaje_electrico = 66.67  (66.67% del tiempo fue electricidad)
 *   porcentaje_mecanico = 33.33   (33.33% del tiempo fue fontaneria)
 */
package com.example.gestion_partes.dto;

public record informe_linea_dto(
        String nombre_obra,
        Double horas_electricas,
        Double horas_mecanicas,
        Double porcentaje_electrico,
        Double porcentaje_mecanico
) {}
