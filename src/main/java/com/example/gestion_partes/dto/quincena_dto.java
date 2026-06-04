/*
 * DTO: quincena_dto (Proyeccion para resumen quincenal)
 *
 * Esta interfaz define los datos que devuelve la base de datos
 * cuando se genera el resumen de una quincena (15 dias).
 * Es una proyeccion JPA para obtener solo los campos necesarios.
 *
 * Agrupa las horas totales trabajadas por cada trabajador en
 * cada obra durante un periodo de 15 dias.
 *
 * Campos que devuelve:
 * - codigo:       Codigo interno del trabajador
 * - nombre:       Nombre del trabajador
 * - apellidos:    Apellidos del trabajador
 * - obra:         Nombre de la obra
 * - total_horas:  Total de horas trabajadas en esa obra en la quincena
 */
package com.example.gestion_partes.dto;

public interface quincena_dto {
    String getCodigo();
    String getNombre();
    String getApellidos();
    String getObra();
    Double getTotal_horas();
}
