package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fechas_permitidas")
public class FechaPermitida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private UUID perfilId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "creado_el")
    private OffsetDateTime creadoEl = OffsetDateTime.now();

    public FechaPermitida() {}

    public FechaPermitida(UUID perfilId, LocalDate fecha) {
        this.perfilId = perfilId;
        this.fecha = fecha;
        this.creadoEl = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getPerfilId() { return perfilId; }
    public LocalDate getFecha() { return fecha; }
    public OffsetDateTime getCreadoEl() { return creadoEl; }

    public void setId(Long id) { this.id = id; }
    public void setPerfilId(UUID perfilId) { this.perfilId = perfilId; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public void setCreadoEl(OffsetDateTime creadoEl) { this.creadoEl = creadoEl; }
}