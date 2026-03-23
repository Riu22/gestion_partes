package com.example.gestion_partes.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.UUID;

public record partes_dto(
    Long id_obra,
    UUID id_perfil,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fecha,
    String descripcion,
    Double horas_normales,
    Double horas_extra
) {
}
