package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * ENTIDAD: partes_jefe (Partes de jefe de obra / encargado)
 *
 * Esta clase representa la tabla "partes_jefe" de la base de datos.
 * Contiene los reportes que realizan los jefes de obra y encargados
 * sobre un periodo de tiempo (normalmente una quincena o semana).
 *
 * A diferencia de los partes de trabajo de los operarios (que son
 * por dia), estos son reportes resumidos de un rango de fechas donde
 * el jefe/encargado detalla las horas dedicadas a cada obra,
 * desglosadas por especialidad (electricidad y fontaneria).
 *
 * Cada parte_jefe contiene una lista de "partes_jefe_obra" que son
 * las lineas de detalle con las horas por cada obra.
 */
@Entity
@Table(name = "partes_jefe", schema = "public")
public class partes_jefe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Perfil del jefe de obra o encargado que crea el reporte
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private perfil perfil;

    // Fecha en que se crea el reporte (por defecto la fecha actual)
    @Column(name = "fecha")
    private LocalDate fecha = LocalDate.now();

    // Descripcion general de las tareas supervisadas en el periodo
    @Column(name = "descripcion_tareas", columnDefinition = "TEXT")
    private String descripcion;

    // Inicio del periodo que cubre este reporte (ej: 01/06/2026)
    private LocalDate fecha_inicio;
    // Fin del periodo que cubre este reporte (ej: 15/06/2026)
    private LocalDate fecha_fin;
    // Total de horas laborables en el periodo (calculado restando festivos y fines de semana)
    private Double total_horas_laborables;

    /*
     * Lista de obras incluidas en este reporte, cada una con sus horas
     * electricas y mecanicas. Cuando se guarda o elimina el parte_jefe,
     * se guardan o eliminan automaticamente sus lineas asociadas.
     */
    @OneToMany(mappedBy = "parteJefe", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("parteJefe")
    private List<partes_jefe_obra> obras = new ArrayList<>();

    // Constructor vacio requerido por JPA
    public partes_jefe() {}

    /*
     * GETTERS Y SETTERS
     *
     * Los metodos "get..." devuelven el valor del campo.
     * Los metodos "set..." permiten modificar el valor del campo.
     */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public perfil getPerfil() { return perfil; }
    public void setPerfil(perfil perfil) { this.perfil = perfil; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<partes_jefe_obra> getObras() { return obras; }
    public void setObras(List<partes_jefe_obra> obras) { this.obras = obras; }
    public LocalDate getFechaInicio() { return fecha_inicio; }
    public void setFechaInicio(LocalDate fecha_inicio) { this.fecha_inicio = fecha_inicio; }
    public LocalDate getFechaFin() { return fecha_fin; }
    public void setFechaFin(LocalDate fecha_fin) { this.fecha_fin = fecha_fin; }
    public Double getTotalHorasLaborables() { return total_horas_laborables; }
    public void setTotalHorasLaborables(Double total_horas_laborables) { this.total_horas_laborables = total_horas_laborables; }
}