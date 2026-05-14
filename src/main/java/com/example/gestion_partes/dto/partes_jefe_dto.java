package com.example.gestion_partes.dto;

import java.time.LocalDate;
import java.util.List;

public record partes_jefe_dto(
        String descripcion,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        List<obra_horas_dto> obras
) {}