/*
 * DTO: informe_jefe_dto (Informe detallado de un parte de jefe)
 *
 * Define la estructura de datos que se devuelve cuando se solicita
 * el informe completo de un parte de jefe/encargado. Contiene los
 * datos generales del reporte mas el detalle de cada obra.
 *
 * Campos que devuelve:
 * - id_parte:              Identificador del parte de jefe
 * - descripcion:           Descripcion de las tareas supervisadas
 * - fecha_inicio:          Inicio del periodo del reporte
 * - fecha_fin:             Fin del periodo del reporte
 * - total_horas_laborables: Total de horas laborables en el periodo
 * - obras:                 Lista de obras con horas electricas/mecanicas
 */
package com.example.gestion_partes.dto;

import java.time.LocalDate;
import java.util.List;

public record informe_jefe_dto(
        Long id_parte,
        String descripcion,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        Double total_horas_laborables,
        List<informe_linea_dto> obras
) {}
