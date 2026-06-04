/*
 * DTO: AusenciaRequestDto (Datos para registrar una ausencia)
 *
 * Define la estructura de datos que se envia desde la aplicacion
 * cuando se quiere registrar un periodo de ausencia de un trabajador
 * (baja medica, vacaciones o permiso de paternidad).
 *
 * Campos:
 * - perfilId:     ID del trabajador que esta ausente (en formato String)
 * - tipo:         Tipo de ausencia (BAJA, VACACIONES, PATERNIDAD)
 * - fechaInicio:  Primer dia de la ausencia
 * - fechaFin:     Ultimo dia de la ausencia
 * - observaciones: Notas o comentarios sobre la ausencia
 * - obraId:       ID de la obra a la que se imputa (opcional, para vacaciones)
 */
package com.example.gestion_partes.dto;

import com.example.gestion_partes.model.AusenciaTipo;

import java.time.LocalDate;

public record AusenciaRequestDto(
        String perfilId,
        AusenciaTipo tipo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String observaciones,
        Long obraId
) {}
