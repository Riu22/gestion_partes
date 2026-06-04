package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/*
 * ENTIDAD: partes_jefe_obra (Detalle de obras en partes de jefe)
 *
 * Esta clase representa la tabla "partes_jefe_obras" de la base de datos.
 * Es el detalle de cada linea dentro de un parte de jefe/encargado.
 *
 * Mientras que "partes_jefe" es el reporte general que cubre un periodo,
 * "partes_jefe_obra" desglosa por cada obra las horas electricas y
 * mecanicas dedicadas en ese periodo, mas los porcentajes calculados.
 *
 * Ejemplo: Un parte_jefe puede tener 3 lineas de partes_jefe_obra:
 *   - Obra "Hotel A": 40h electricas, 20h mecanicas (67% elec, 33% mec)
 *   - Obra "Colegio B": 30h electricas, 0h mecanicas (100% elec, 0% mec)
 *   - Obra "Hospital C": 10h electricas, 50h mecanicas (17% elec, 83% mec)
 */
@Entity
@Table(name = "partes_jefe_obras", schema = "public")
public class partes_jefe_obra {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencia al parte de jefe al que pertenece esta linea
    @JsonBackReference("parteJefe-obras")
    @ManyToOne @JoinColumn(name = "parte_jefe_id")
    private partes_jefe parteJefe;

    // Obra a la que corresponden estas horas
    @ManyToOne
    @JoinColumn(name = "obra_id")
    @JsonIgnoreProperties({"asignaciones", "partes"})
    private obra obra;

    // Horas dedicadas a trabajos electricos en esta obra
    private Double horas_electricas;
    // Horas dedicadas a trabajos mecanicos/fontaneria en esta obra
    private Double horas_mecanicas;

    // Porcentaje de horas electricas respecto al total de la obra (calculado automaticamente)
    private Double porcentaje_electrico;
    // Porcentaje de horas mecanicas respecto al total de la obra (calculado automaticamente)
    private Double porcentaje_mecanico;

    // Constructor vacio requerido por JPA
    public partes_jefe_obra() {}

    /*
     * Constructor que crea una linea de detalle con todos los datos
     * Recibe: el parte_jefe al que pertenece, la obra, las horas de cada tipo y los porcentajes
     */
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

    /*
     * GETTERS Y SETTERS
     */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public partes_jefe getParteJefe() { return parteJefe; }
    public void setParteJefe(partes_jefe parteJefe) { this.parteJefe = parteJefe; }
    public obra getObra() { return obra; }
    public void setObra(obra obra) { this.obra = obra; }
    public Double getHoras_electricas() { return horas_electricas; }
    public void setHoras_electricas(Double horas_electricas) { this.horas_electricas = horas_electricas; }
    public Double getHoras_mecanicas() { return horas_mecanicas; }
    public void setHoras_mecanicas(Double horas_mecanicas) { this.horas_mecanicas = horas_mecanicas; }
    public Double getPorcentaje_electrico() { return porcentaje_electrico; }
    public void setPorcentaje_electrico(Double porcentaje_electrico) { this.porcentaje_electrico = porcentaje_electrico; }
    public Double getPorcentaje_mecanico() { return porcentaje_mecanico; }
    public void setPorcentaje_mecanico(Double porcentaje_mecanico) { this.porcentaje_mecanico = porcentaje_mecanico; }
}