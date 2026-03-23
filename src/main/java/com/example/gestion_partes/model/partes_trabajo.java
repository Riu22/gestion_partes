package com.example.gestion_partes.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "partes_trabajo", schema = "public")
public class partes_trabajo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_obra_id", nullable = false) // Coincide con el SQL
    private obra obra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false) // Coincide con el SQL
    private perfil perfil;

    private LocalDate fecha;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "horas_normales")
    private Double horas_normales = 8.0;

    @Column(name = "horas_extra")
    private Double horas_extra = 0.0;

    private boolean firmado = false;
    public partes_trabajo() {
    }

    public partes_trabajo(LocalDate fecha, String descripcion, Double horas_normales, Double horas_extra) {
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.horas_normales = horas_normales;
        this.horas_extra = horas_extra;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public obra getObra() {
        return obra;
    }

    public void setObra(obra obra) {
        this.obra = obra;
    }

    public perfil getPerfil() {
        return perfil;
    }

    public void setPerfil(perfil perfil) {
        this.perfil = perfil;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Double getHoras_normales() {
        return horas_normales;
    }

    public void setHoras_normales(Double horas_normales) {
        this.horas_normales = horas_normales;
    }

    public Double getHoras_extra() {
        return horas_extra;
    }

    public void setHoras_extra(Double horas_extra) {
        this.horas_extra = horas_extra;
    }

    public boolean isFirmado() {
        return firmado;
    }

    public void setFirmado(boolean firmado) {
        this.firmado = firmado;
    }
}
