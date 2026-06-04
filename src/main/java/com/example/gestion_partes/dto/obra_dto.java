/*
 * DTO: obra_dto (Datos de una obra/proyecto)
 *
 * Define la estructura de datos para crear o modificar una obra
 * en el sistema. Se utiliza cuando desde la aplicacion se envia
 * la informacion de una nueva obra o se modifica una existente.
 *
 * Campos:
 * - nombre:      Nombre descriptivo de la obra (ej: "Reformas Hotel Palace")
 * - direccion:   Direccion donde se ubica la obra
 * - municipio:   Municipio de la obra
 * - poblacion:   Poblacion de la obra
 * - codigo:      Codigo interno de la empresa para identificar la obra
 * - activa:      Indica si la obra esta actualmente en ejecucion (true) o parada (false)
 */
package com.example.gestion_partes.dto;

public record obra_dto(
        String nombre,
        String direccion,
        String municipio,
        String poblacion,
        String codigo,
        Boolean activa,
        Boolean postventa
) {
}
