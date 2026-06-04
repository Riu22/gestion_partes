/*
 * DTO: partes_dto (Datos de un parte de trabajo)
 *
 * Esta clase define la estructura de datos que se recibe desde la
 * aplicacion movil cuando se quiere crear o modificar un parte de
 * trabajo diario. Es como un formulario con los campos que el
 * trabajador debe rellenar.
 *
 * "record" significa que es una clase inmutable que solo transporta
 * datos, no tiene logica. Java genera automaticamente los getters,
 * constructor, equals, hashCode y toString.
 *
 * Campos que recibe:
 * - id_obra:       Identificador de la obra donde se trabajo (obligatorio)
 * - id_perfil:     Identificador del trabajador que realiza el parte
 * - fecha:         Fecha del trabajo (no puede ser futura)
 * - descripcion:   Texto explicando las tareas realizadas
 * - horas_normales: Horas ordinarias trabajadas (por defecto 8)
 * - horas_extra:   Horas extras realizadas
 * - especialidad:  Tipo de trabajo (ELECTRICIDAD o FONTANERIA)
 * - firma_base64:  Imagen de la firma digital en formato base64 (texto codificado)
 * - nombre_firmado: Nombre de la persona que firmo
 * - trabajo_extra: Descripcion de trabajos adicionales
 */
package com.example.gestion_partes.dto;

import com.example.gestion_partes.model.especialidad;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.UUID;

public record partes_dto(
        Long id_obra,
        UUID id_perfil,
        @NotNull @PastOrPresent @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fecha,
        String descripcion,
        Double horas_normales,
        Double horas_extra,
        especialidad especialidad,
        String firma_base64,
        String nombre_firmado,
        @JsonProperty("trabajos_extra") String trabajo_extra
) {}
