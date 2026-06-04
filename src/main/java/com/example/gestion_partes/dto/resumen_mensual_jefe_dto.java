/*
 * DTO: resumen_mensual_jefe_dto (Resumen mensual para jefe de obra)
 *
 * Define la estructura de datos que se devuelve cuando se solicita
 * el resumen de un mes completo para un jefe de obra. Agrupa todos
 * los partes del jefe en ese mes con un desglose por obras.
 *
 * Campos que devuelve:
 * - anio:                Anio del resumen (ej: 2026)
 * - mes:                 Mes del resumen (1=enero, 2=febrero, ..., 12=diciembre)
 * - total_horas_laborables: Total de horas laborables en el mes
 * - obras:               Resumen de todas las obras con sus horas y porcentajes
 * - partes:              Lista de los partes individuales del mes
 */
package com.example.gestion_partes.dto;

import java.util.List;

public record resumen_mensual_jefe_dto(
        int anio,
        int mes,
        Double total_horas_laborables,
        List<resumen_obra_dto> obras,
        List<resumen_parte_dto> partes
) {}