package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * ENTIDAD: FechaPermitida (Fechas habilitadas para editar partes antiguos)
 *
 * Esta clase representa la tabla "fechas_permitidas" de la base de datos.
 *
 * Por norma general, los trabajadores solo pueden crear o modificar
 * partes de trabajo de los ultimos 14 dias. Pero en ocasiones especiales,
 * los administradores pueden habilitar fechas pasadas concretas para
 * que un trabajador pueda editar o crear partes antiguos.
 *
 * Esta tabla almacena esas excepciones: que usuario tiene permitido
 * modificar que fecha concreta.
 *
 * Ejemplo: Si hoy es 4 de junio y queremos que Juan pueda registrar
 * un parte del 1 de enero (que ya esta fuera del limite de 14 dias),
 * se crea un registro FechaPermitida con perfilId=Juan y fecha=1 de enero.
 */
@Entity
@Table(name = "fechas_permitidas")
public class FechaPermitida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del trabajador al que se le permite editar esa fecha
    @Column(name = "perfil_id", nullable = false)
    private UUID perfilId;

    // La fecha concreta que se habilita para editar
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    // Momento en que se creo esta habilitacion
    @Column(name = "creado_el")
    private OffsetDateTime creadoEl = OffsetDateTime.now();

    // Constructor vacio requerido por JPA
    public FechaPermitida() {}

    /*
     * Constructor que crea una habilitacion de fecha para un trabajador
     * Recibe: el ID del trabajador y la fecha que se quiere habilitar
     */
    public FechaPermitida(UUID perfilId, LocalDate fecha) {
        this.perfilId = perfilId;
        this.fecha = fecha;
        this.creadoEl = OffsetDateTime.now();
    }

    /*
     * GETTERS Y SETTERS
     */

    public Long getId() { return id; }
    public UUID getPerfilId() { return perfilId; }
    public LocalDate getFecha() { return fecha; }
    public OffsetDateTime getCreadoEl() { return creadoEl; }
    public void setId(Long id) { this.id = id; }
    public void setPerfilId(UUID perfilId) { this.perfilId = perfilId; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public void setCreadoEl(OffsetDateTime creadoEl) { this.creadoEl = creadoEl; }
}