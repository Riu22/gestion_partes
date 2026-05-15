package com.example.gestion_partes.dto;

import java.time.LocalDate;

public record resumen_parte_dto(
        Long id,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        Double total_horas_laborables,
        String descripcion
) {}