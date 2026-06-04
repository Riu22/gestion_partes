/*
 * DTO: asignacion_dto (Datos para asignar un operario a un encargado)
 *
 * Define la estructura para crear una relacion jerarquica entre
 * un encargado y un operario en una obra concreta.
 *
 * Esto permite que un ENCARGADO supervise a uno o varios OPERARIOS
 * en una obra determinada.
 *
 * Campos:
 * - encargadoId:  ID del encargado (supervisor)
 * - operarioId:   ID del operario (trabajador a supervisar)
 * - obraId:       ID de la obra donde se establece la relacion
 */
package com.example.gestion_partes.dto;

import java.util.UUID;

public record asignacion_dto(
        UUID encargadoId,
        UUID operarioId,
        Long obraId
) {}