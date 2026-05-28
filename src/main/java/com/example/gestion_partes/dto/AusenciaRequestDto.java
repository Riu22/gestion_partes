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
