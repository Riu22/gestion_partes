package com.example.gestion_partes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * ENTIDAD: Ausencia (Ausencias laborales de los trabajadores)
 *
 * Esta clase representa la tabla "ausencias" de la base de datos.
 * Registra los periodos en los que un trabajador no ha estado
 * disponible para trabajar, ya sea por baja medica, vacaciones
 * o permiso de paternidad.
 *
 * Cada ausencia tiene un rango de fechas (inicio y fin) y un tipo.
 * Tambien puede asociarse opcionalmente a una obra concreta
 * (por ejemplo, si las vacaciones se imputan a una obra especifica).
 *
 * Este registro es importante para la contabilidad, porque los
 * dias de ausencia no deben aparecer como "dias sin parte" en
 * los informes, sino como ausencias justificadas.
 */
@Entity
@Table(name = "ausencias")
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del trabajador que esta ausente (referencia al UUID de perfiles)
    @Column(name = "perfil_id", nullable = false)
    private UUID perfilId;

    /*
     * ID de la obra a la que se imputa la ausencia (opcional).
     * Por ejemplo, si un trabajador esta de vacaciones, se puede
     * indicar a que obra se cargan esos dias de vacaciones.
     */
    @Column(name = "obra_id")
    private Long obraId;

    // Tipo de ausencia: BAJA (medica), VACACIONES o PATERNIDAD
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AusenciaTipo tipo;

    // Primer dia de la ausencia
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Ultimo dia de la ausencia
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    // Observaciones o notas adicionales sobre la ausencia
    @Column(name = "observaciones")
    private String observaciones;

    // Fecha y hora en que se registro la ausencia en el sistema
    @Column(name = "creado_el")
    private OffsetDateTime creadoEl = OffsetDateTime.now();

    // Constructor vacio requerido por JPA
    public Ausencia() {}

    /*
     * Constructor para crear una ausencia completa
     * Recibe: el ID del trabajador, el tipo de ausencia,
     *         la fecha de inicio, la fecha de fin y observaciones
     */
    public Ausencia(UUID perfilId, AusenciaTipo tipo,
                    LocalDate fechaInicio, LocalDate fechaFin,
                    String observaciones) {
        this.perfilId = perfilId;
        this.tipo = tipo;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.observaciones = observaciones;
        this.creadoEl = OffsetDateTime.now();
    }

    /*
     * GETTERS Y SETTERS
     */

    public Long getId() { return id; }
    public UUID getPerfilId() { return perfilId; }
    public AusenciaTipo getTipo() { return tipo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public String getObservaciones() { return observaciones; }
    public OffsetDateTime getCreadoEl() { return creadoEl; }
    public void setId(Long id) { this.id = id; }
    public void setPerfilId(UUID perfilId) { this.perfilId = perfilId; }
    public void setTipo(AusenciaTipo tipo) { this.tipo = tipo; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public void setCreadoEl(OffsetDateTime creadoEl) { this.creadoEl = creadoEl; }
    public Long getObraId() { return obraId; }
    public void setObraId(Long obraId) { this.obraId = obraId; }
}