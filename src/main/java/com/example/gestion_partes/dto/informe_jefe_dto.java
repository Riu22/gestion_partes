package com.example.gestion_partes.dto;

import java.time.LocalDate;
import java.util.List;

// dto/informe_jefe_dto.java
public record informe_jefe_dto(
        Long id_parte,
        String descripcion,
        LocalDate fecha_inicio,
        LocalDate fecha_fin,
        Double total_horas_laborables,
        List<informe_linea_dto> obras
) {}

// dto/informe_linea_dto.java
