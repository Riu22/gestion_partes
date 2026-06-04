/*
 * DTO: resumen_parte_dto (Resumen de un parte individual en el informe mensual)
 *
 * Define los datos de un parte de jefe individual dentro del
 * resumen mensual. Cada parte cubre un periodo (semana/quincena)
 * dentro del mes.
 *
 * Campos que devuelve:
 * - id:                    Identificador del parte de jefe
 * - fecha_inicio:          Inicio del periodo del parte
 * - fecha_fin:             Fin del periodo del parte
 * - total_horas_laborables: Horas laborables en ese periodo
 * - descripcion:           Descripcion de las tareas
 */
package com.example.gestion_partes.dto;

import java.time.LocalDate;

public record resumen_parte_dto(
        Long id,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        Double total_horas_laborables,
        String descripcion
) {}