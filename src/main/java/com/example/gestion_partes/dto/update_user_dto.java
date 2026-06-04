/*
 * DTO: update_user_dto (Datos para modificar un usuario existente)
 *
 * Define la estructura de datos que se envia cuando se quiere
 * modificar la informacion de un trabajador ya existente.
 *
 * Todos los campos son OPCIONALES (se puede enviar solo los que
 * se quieran cambiar). Si un campo no se envia, se mantiene el
 * valor anterior.
 *
 * Campos modificables:
 * - name:               Nuevo nombre
 * - apellidos:          Nuevos apellidos
 * - rol:                Nuevo rol/puesto
 * - activo:             Activar o desactivar el usuario
 * - codigo:             Nuevo codigo interno
 * - especialidad:       Nueva especialidad (se recibe como String)
 * - postventa:          Cambiar si es de postventa
 * - grupo_profesional:  Nuevo grupo profesional
 * - email:              Nuevo correo electronico
 * - password:           Nueva contrasena
 */
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
        String grupo_profesional,
        String email,
        String password
)
{}
