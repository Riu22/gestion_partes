package com.example.gestion_partes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ausencias")
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private UUID perfilId;

    @Column(name = "obra_id")
    private Long obraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AusenciaTipo tipo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "creado_el")
    private OffsetDateTime creadoEl = OffsetDateTime.now();

    public Ausencia() {}

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

    public Long getObraId() {
        return obraId;
    }

    public void setObraId(Long obraId) {
        this.obraId = obraId;
    }
}