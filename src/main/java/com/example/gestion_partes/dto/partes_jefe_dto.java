package com.example.gestion_partes.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record partes_jefe_dto(
        @NotNull String descripcion,
        @NotEmpty List<obra_porcentaje_dto> obras
) {}