package com.example.gestion_partes.dto;

public record resumen_obra_dto(
        String nombre_obra,
        String codigo_obra,
        Double horas_electricas,
        Double horas_mecanicas,
        Double porcentaje_electrico,
        Double porcentaje_mecanico
) {}