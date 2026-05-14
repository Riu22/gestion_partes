package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

// model/partes_jefe_obra.java
@Entity
@Table(name = "partes_jefe_obras", schema = "public")
public class partes_jefe_obra {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference("parteJefe-obras")
    @ManyToOne @JoinColumn(name = "parte_jefe_id")
    private partes_jefe parteJefe;

    // partes_jefe_obra.java
    @ManyToOne
    @JoinColumn(name = "obra_id")
    @JsonIgnoreProperties({"asignaciones", "partes"})
    private obra obra;

    private Double horas_electricas;
    private Double horas_mecanicas;

    // Porcentajes calculados (se almacenan para consulta rápida)
    private Double porcentaje_electrico;
    private Double porcentaje_mecanico;

    public partes_jefe_obra() {}
    public partes_jefe_obra(partes_jefe parteJefe, obra obra,
                            Double horas_electricas, Double horas_mecanicas,
                            Double porcentaje_electrico, Double porcentaje_mecanico) {
        this.parteJefe = parteJefe;
        this.obra = obra;
        this.horas_electricas = horas_electricas;
        this.horas_mecanicas = horas_mecanicas;
        this.porcentaje_electrico = porcentaje_electrico;
        this.porcentaje_mecanico = porcentaje_mecanico;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public partes_jefe getParteJefe() {
        return parteJefe;
    }

    public void setParteJefe(partes_jefe parteJefe) {
        this.parteJefe = parteJefe;
    }

    public obra getObra() {
        return obra;
    }

    public void setObra(obra obra) {
        this.obra = obra;
    }

    public Double getHoras_electricas() {
        return horas_electricas;
    }

    public void setHoras_electricas(Double horas_electricas) {
        this.horas_electricas = horas_electricas;
    }

    public Double getHoras_mecanicas() {
        return horas_mecanicas;
    }

    public void setHoras_mecanicas(Double horas_mecanicas) {
        this.horas_mecanicas = horas_mecanicas;
    }

    public Double getPorcentaje_electrico() {
        return porcentaje_electrico;
    }

    public void setPorcentaje_electrico(Double porcentaje_electrico) {
        this.porcentaje_electrico = porcentaje_electrico;
    }

    public Double getPorcentaje_mecanico() {
        return porcentaje_mecanico;
    }

    public void setPorcentaje_mecanico(Double porcentaje_mecanico) {
        this.porcentaje_mecanico = porcentaje_mecanico;
    }
}