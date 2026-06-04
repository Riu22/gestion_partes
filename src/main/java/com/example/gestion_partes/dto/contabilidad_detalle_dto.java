/*
 * DTO: contabilidad_detalle_dto (Proyeccion para detalle de contabilidad)
 *
 * Esta interfaz define la estructura de los datos que devuelve
 * la base de datos cuando se consulta el detalle de contabilidad
 * de un periodo. Es una "proyeccion" de JPA que permite obtener
 * solo los campos necesarios sin cargar entidades completas.
 *
 * Se usa para generar informes detallados dia por dia de las horas
 * trabajadas por cada operario en cada obra.
 *
 * Campos que devuelve:
 * - codigo:             Codigo interno del trabajador
 * - nombre:             Nombre del trabajador
 * - apellidos:          Apellidos del trabajador
 * - grupo_profesional:  Categoria profesional del trabajador
 * - obra_nombre:        Nombre de la obra donde trabajo
 * - obraId:             Identificador de la obra
 * - fecha:              Fecha del parte de trabajo
 * - horas_totales:      Total de horas trabajadas ese dia (normales + extra)
 * - especialidad:       Especialidad del trabajo realizado
 * - parteId:            Identificador del parte de trabajo
 */
package com.example.gestion_partes.dto;

import java.time.LocalDate;

public interface contabilidad_detalle_dto {
    String    getCodigo();
    String    getNombre();
    String    getApellidos();
    String    getGrupo_profesional();
    String    getObra_nombre();
    Long      getObraId();
    LocalDate getFecha();
    Double    getHoras_totales();
    String    getEspecialidad();
    Long      getParteId();
}