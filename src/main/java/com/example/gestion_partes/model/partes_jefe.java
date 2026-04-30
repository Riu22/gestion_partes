package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "partes_jefe", schema = "public")
public class partes_jefe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private perfil perfil;

    @Column(name = "fecha")
    private LocalDate fecha = LocalDate.now();

    @Column(name = "descripcion_tareas", columnDefinition = "TEXT")
    private String descripcion;

    @OneToMany(mappedBy = "parteJefe", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<partes_jefe_obra> obras = new ArrayList<>();

    public partes_jefe() {}

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
}