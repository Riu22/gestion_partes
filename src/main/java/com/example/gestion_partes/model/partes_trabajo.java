package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Entity
@Table(name = "partes_trabajo", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class partes_trabajo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "point_obra_id", nullable = false) // Coincide con el SQL
    private obra obra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false) // Coincide con el SQL
    private perfil perfil;

    private LocalDate fecha;

    @Column(columnDefinition = "TEXT",name = "descripcion_tareas")
    private String descripcion;

    @Column(name = "horas_normales")
    private Double horas_normales = 8.0;

    @Column(name = "horas_extra")
    private Double horas_extra = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "especialidad")
    private especialidad especialidad;

    public especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(especialidad especialidad) { this.especialidad = especialidad; }

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
        LocalDate limiteMinimo = LocalDate.now().minusWeeks(2);
        if (fecha.isBefore(limiteMinimo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No puedes crear partes con más de 2 semanas de antigüedad"
            );
        }
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
}
