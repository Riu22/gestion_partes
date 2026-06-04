/*
 * DTO: user_dto (Datos simples de usuario para inicio de sesion)
 *
 * (Actualmente no se usa en el codigo principal, la autenticacion
 * se hace mediante Supabase Auth con JWT)
 *
 * Estructura simple con correo y contrasena para iniciar sesion.
 * Mantenido por compatibilidad.
 */
package com.example.gestion_partes.dto;

public record user_dto(
        String email,
        String password
) {
}
