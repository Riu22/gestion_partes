package com.example.gestion_partes.dto;

public record obra_dto(
        String nombre,
        String direccion,
        String municipio,
        String poblacion,
        String codigo,
        Boolean activa
) {
}
