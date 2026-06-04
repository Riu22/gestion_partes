package com.example.gestion_partes.model;

/*
 * ENUM: AusenciaTipo (tipos de ausencia laboral)
 *
 * Define los distintos motivos por los que un trabajador puede
 * estar ausente del trabajo. Se usa para registrar y justificar
 * los dias que un empleado no ha trabajado.
 *
 * Valores posibles:
 * - BAJA:        baja medica / incapacidad temporal (el trabajador esta enfermo)
 * - VACACIONES:  dias de vacaciones disfrutados
 * - PATERNIDAD:  permiso por paternidad / maternidad
 */
public enum AusenciaTipo {
    BAJA,
    VACACIONES,
    PATERNIDAD
}