package com.example.gestion_partes.model;

import jakarta.persistence.*;

@Entity
@Table(name = "partes_jefe_obras", schema = "public")
public class partes_jefe_obra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parte_jefe_id", nullable = false)
    private partes_jefe parteJefe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_id")
    private obra obra;

    @Column(name = "porcentaje", columnDefinition = "numeric(5,2)")
    private Double porcentaje;

    public partes_jefe_obra() {}

    public partes_jefe_obra(partes_jefe parteJefe, obra obra, Double porcentaje) {
        this.parteJefe = parteJefe;
        this.obra = obra;
        this.porcentaje = porcentaje;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public partes_jefe getParteJefe() { return parteJefe; }
    public void setParteJefe(partes_jefe parteJefe) { this.parteJefe = parteJefe; }
    public obra getObra() { return obra; }
    public void setObra(obra obra) { this.obra = obra; }
    public Double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(Double porcentaje) { this.porcentaje = porcentaje; }
}