/*
 * DTO: partes_jefe_dto (Datos de un parte de jefe/encargado)
 *
 * Define la estructura de datos que se recibe desde la aplicacion
 * cuando un jefe de obra o encargado crea o modifica un reporte
 * de un periodo de tiempo (semana o quincena).
 *
 * Este DTO contiene:
 * - descripcion:    Descripcion general de las tareas supervisadas
 * - fecha_inicio:   Primer dia del periodo que cubre el reporte
 * - fecha_fin:      Ultimo dia del periodo que cubre el reporte
 * - obras:          Lista de obras con las horas dedicadas a cada una
 *                   (cada obra lleva horas_electricas y horas_mecanicas)
 */
package com.example.gestion_partes.dto;

import java.time.LocalDate;
import java.util.List;

public record partes_jefe_dto(
        String descripcion,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        List<obra_horas_dto> obras
) {}