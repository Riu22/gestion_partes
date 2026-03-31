package com.example.gestion_partes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record obra_porcentaje_dto(
        @NotNull Long id_obra,
        @NotNull @Min(1) @Max(100) Double porcentaje
) {}