package com.example.gestion_partes.model;

/*
 * ENUM: user_rol (roles de usuario en el sistema)
 *
 * Este enumerado define los distintos roles o puestos que puede tener
 * una persona dentro de la empresa de instalaciones. Cada rol tiene
 * distintos permisos y niveles de acceso en la aplicacion.
 *
 * Los roles ordenados de mayor a menor jerarquia son:
 *
 * - ADMINISTRACION:  acceso total al sistema, puede crear/borrar usuarios,
 *                    gestionar obras, ver todos los partes y generar informes.
 *                    Es el rol con maxima jerarquia.
 *
 * - GESTION:         puede gestionar usuarios y obras, ver informes,
 *                    pero no tiene permisos de administrador total.
 *
 * - JEFE_DE_OBRA:    supervisor general de una o varias obras. Puede ver los
 *                    partes de todos los trabajadores en sus obras asignadas
 *                    y generar informes de produccion mensuales.
 *
 * - ENCARGADO:       encargado o capataz de una obra concreta. Supervisa
 *                    a los operarios directamente y puede ver sus partes.
 *
 * - OPERARIO:        trabajador base que realiza las tareas en obra.
 *                    Solo puede registrar sus propios partes de trabajo
 *                    y ver su propio historial.
 */
public enum user_rol {
    ADMINISTRACION,
    OPERARIO,
    JEFE_DE_OBRA,
    ENCARGADO,
    GESTION
}
