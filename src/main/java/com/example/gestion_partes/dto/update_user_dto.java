package com.example.gestion_partes.dto;

import com.example.gestion_partes.model.user_rol;

public record update_user_dto(
        String name,
        String apellidos,
        user_rol rol,
        Boolean activo,
        String codigo,
        String especialidad,
        Boolean postventa,
        String grupo_profesional
)
{}
