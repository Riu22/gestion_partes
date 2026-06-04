/*
 * DTO: HoraDia (Horas de un parte y su identificador)
 *
 * Estructura simple que asocia un identificador de parte de trabajo
 * con la cantidad de horas registradas en ese parte.
 *
 * Se usa cuando se necesita consultar solo las horas totales de
 * un parte concreto, sin necesidad de todos los demas datos.
 *
 * Campos:
 * - horas:    Total de horas del parte (normales + extra)
 * - parteId:  Identificador del parte de trabajo
 */
package com.example.gestion_partes.dto;

public record HoraDia(double horas, Long parteId) {}

