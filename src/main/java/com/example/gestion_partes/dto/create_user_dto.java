package com.example.gestion_partes.dto;

import com.example.gestion_partes.model.especialidad;
import com.example.gestion_partes.model.user_rol;

public record create_user_dto(
        String email,
        String name,
        String apellidos,
        String password,
        user_rol rol,
        String codigo,
        boolean activo,
        Boolean postventa,
        especialidad especialidad
) {
}
