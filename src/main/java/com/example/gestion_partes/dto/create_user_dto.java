/*
 * DTO: create_user_dto (Datos para crear un nuevo usuario)
 *
 * Define la estructura de datos que se envia desde la aplicacion
 * cuando un administrador quiere dar de alta a un nuevo trabajador
 * en el sistema.
 *
 * Todos los campos necesarios para crear un perfil de usuario:
 * - email:              Correo electronico del usuario (usado para iniciar sesion)
 * - name:               Nombre del trabajador
 * - apellidos:          Apellidos del trabajador
 * - password:           Contrasena para acceder al sistema
 * - rol:                Rol o puesto en la empresa (ADMINISTRACION, OPERARIO, etc.)
 * - codigo:             Codigo numerico interno del trabajador
 * - activo:             Si el usuario empieza activo (true) o desactivado (false)
 * - postventa:          Si pertenece al departamento de postventa
 * - especialidad:       Especialidad (ELECTRICIDAD o FONTANERIA)
 * - grupo_profesional:  Grupo profesional / categoria laboral (ej: "Oficial 1a")
 */
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
        especialidad especialidad,
        String grupo_profesional
) {
}
