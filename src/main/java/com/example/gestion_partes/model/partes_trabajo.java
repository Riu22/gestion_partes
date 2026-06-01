package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "partes_trabajo", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class partes_trabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "point_obra_id", nullable = false)
    private obra obra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private perfil perfil;

    private LocalDate fecha;

    @Column(columnDefinition = "TEXT", name = "descripcion_tareas")
    private String descripcion;

    @Column(name = "horas_normales", columnDefinition = "numeric(5,2)")
    private Double horas_normales = 8.0;

    @Column(name = "horas_extra", columnDefinition = "numeric(5,2)")
    private Double horas_extra = 0.0;

    @Column(name = "firma_url")
    private String firma_url;

    @Column(name = "nombre_firmado")
    private String nombre_firmado;

    @Enumerated(EnumType.STRING)
    @Column(name = "especialidad")
    private especialidad especialidad;

    @Column(name = "trabajos_extra")
    private String trabajos_extra;

    // true cuando un ADMINISTRACION o GESTION crea el parte para otro usuario
    @Column(name = "creado_por_gestor", nullable = false)
    private boolean creado_por_gestor = false;

    // ─── Constructores ─────────────────────────────────────────────────────────

    public partes_trabajo() {}

    public partes_trabajo(
            LocalDate fecha,
            String descripcion,
            Double horas_normales,
            Double horas_extra) {
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.horas_normales = horas_normales;
        this.horas_extra = horas_extra;
    }

    // ─── Getters y setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public obra getObra() { return obra; }
    public void setObra(obra obra) { this.obra = obra; }

    public perfil getPerfil() { return perfil; }
    public void setPerfil(perfil perfil) { this.perfil = perfil; }

    public LocalDate getFecha() { return fecha; }


    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Double getHoras_normales() { return horas_normales; }
    public void setHoras_normales(Double horas_normales) { this.horas_normales = horas_normales; }

    public Double getHoras_extra() { return horas_extra; }
    public void setHoras_extra(Double horas_extra) { this.horas_extra = horas_extra; }

    public especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(especialidad especialidad) { this.especialidad = especialidad; }

    public boolean isCreado_por_gestor() { return creado_por_gestor; }
    public void setCreado_por_gestor(boolean creado_por_gestor) {
        this.creado_por_gestor = creado_por_gestor;
    }

    public String getFirma_url() {
        return firma_url;
    }

    public void setFirma_url(String firma_url) {
        this.firma_url = firma_url;
    }

    public String getNombre_firmado() {
        return nombre_firmado;
    }

    public void setNombre_firmado(String nombre_firmado) {
        this.nombre_firmado = nombre_firmado;
    }

    public String getTrabajos_extra() {
        return trabajos_extra;
    }

    public void setTrabajos_extra(String trabajos_extra) {
        this.trabajos_extra = trabajos_extra;
    }
}