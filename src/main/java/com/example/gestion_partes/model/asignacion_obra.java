package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * ENTIDAD: asignacion_obra (Asignacion de trabajadores a obras)
 *
 * Esta clase representa la tabla "asignaciones_obra" de la base de datos.
 * Es una tabla intermedia que relaciona a los trabajadores (perfiles)
 * con las obras en las que trabajan.
 *
 * Una misma persona puede estar asignada a varias obras, y una misma
 * obra puede tener varias personas asignadas (relacion muchos a muchos).
 *
 * Por ejemplo:
 *   - Juan Perez (OPERARIO) -> Obra "Hotel Miramar"
 *   - Juan Perez (OPERARIO) -> Obra "Colegio San Jose"
 *   - Maria Lopez (ENCARGADO) -> Obra "Hotel Miramar"
 */
@Entity
@Table(name = "asignaciones_obra", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class asignacion_obra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El trabajador (perfil) asignado a la obra
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", nullable = false)
    private perfil perfil;

    // La obra a la que se asigna el trabajador
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_id", nullable = false)
    private obra obra;

    // Fecha y hora en que se realizo la asignacion
    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion = LocalDateTime.now();

    // Constructor vacio requerido por JPA
    public asignacion_obra() {}

    /*
     * Constructor que crea una asignacion entre un trabajador y una obra
     * Recibe: el perfil del trabajador y la obra a asignar
     * La fecha de asignacion se pone automaticamente a la fecha/hora actual
     */
    public asignacion_obra(perfil perfil, obra obra) {
        this.perfil = perfil;
        this.obra = obra;
        this.fechaAsignacion = LocalDateTime.now();
    }

    /*
     * GETTERS Y SETTERS
     */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public perfil getPerfil() { return perfil; }
    public void setPerfil(perfil perfil) { this.perfil = perfil; }
    public obra getObra() { return obra; }
    public void setObra(obra obra) { this.obra = obra; }
    public LocalDateTime getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(LocalDateTime fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }
}