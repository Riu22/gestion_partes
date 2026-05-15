package com.example.gestion_partes.dto;

import java.util.List;

public record resumen_mensual_jefe_dto(
        int anio,
        int mes,
        Double total_horas_laborables,
        List<resumen_obra_dto> obras,
        List<resumen_parte_dto> partes
) {}