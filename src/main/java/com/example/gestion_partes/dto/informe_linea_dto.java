package com.example.gestion_partes.dto;

public record informe_linea_dto(
        String nombre_obra,
        Double horas_electricas,
        Double horas_mecanicas,
        Double porcentaje_electrico,   // e.g. 3.0 → "3% de eléctricas"
        Double porcentaje_mecanico     // e.g. 2.0 → "2% de mecánicas"
) {}
