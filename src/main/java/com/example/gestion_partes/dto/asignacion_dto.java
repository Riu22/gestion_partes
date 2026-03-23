package com.example.gestion_partes.dto;

import java.util.UUID;

public record asignacion_dto(
        UUID encargadoId,
        UUID operarioId,
        Long obraId
) {}