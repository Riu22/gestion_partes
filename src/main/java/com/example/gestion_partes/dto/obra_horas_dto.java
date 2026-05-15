package com.example.gestion_partes.dto;

import java.util.UUID;

public record obra_horas_dto(
        Long id_obra,
        Double horas_electricas,
        Double horas_mecanicas
) {}