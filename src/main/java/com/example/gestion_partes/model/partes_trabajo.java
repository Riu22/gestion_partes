package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

/*
 * ENTIDAD: partes_trabajo (Partes de trabajo diarios)
 *
 * Esta clase representa la tabla "partes_trabajo" de la base de datos.
 * Almacena el registro de trabajo de un operario en una obra concreta
 * durante un dia especifico. Es la entidad principal del sistema,
 * ya que aqui es donde los trabajadores registran sus horas.
 *
 * Cada parte de trabajo contiene:
 * - La obra donde se trabajo
 * - El operario que trabajo
 * - La fecha del trabajo
 * - Descripcion de las tareas realizadas
 * - Horas normales trabajadas (por defecto 8 horas)
 * - Horas extra realizadas
 * - La especialidad del trabajo (electricidad o fontaneria)
 * - La firma digital del trabajador (imagen)
 * - El nombre de quien firmo
 * - Notas de trabajos extra si los hubo
 * - Si fue creado por un gestor/admin (no por el propio trabajador)
 */
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

    // Indica si el parte fue creado por un ADMINISTRACION o GESTION
    // en nombre del trabajador (true) o por el propio trabajador (false)
    @Column(name = "creado_por_gestor", nullable = false)
    private boolean creado_por_gestor = false;

    // ─── Constructores ─────────────────────────────────────────────────────────

    // Constructor vacio requerido por JPA (Hibernate) para crear objetos
    public partes_trabajo() {}

    /*
     * Constructor para crear un parte de trabajo con los datos esenciales
     * Recibe: la fecha del trabajo, descripcion de tareas,
     *         horas normales y horas extra realizadas
     */
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

    /*
     * GETTERS Y SETTERS
     *
     * Los metodos "get..." devuelven el valor del campo correspondiente.
     * Los metodos "set..." permiten modificar el valor del campo.
     */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    // Obra: la obra o proyecto donde se realizo el trabajo
    public obra getObra() { return obra; }
    public void setObra(obra obra) { this.obra = obra; }

    // Perfil: el trabajador que realizo el trabajo
    public perfil getPerfil() { return perfil; }
    public void setPerfil(perfil perfil) { this.perfil = perfil; }

    // Fecha: el dia en que se realizo el trabajo
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    // Descripcion: explicacion de las tareas realizadas ese dia
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    // Horas normales: horas ordinarias trabajadas (jornada normal, tipicamente 8h)
    public Double getHoras_normales() { return horas_normales; }
    public void setHoras_normales(Double horas_normales) { this.horas_normales = horas_normales; }

    // Horas extra: horas adicionales fuera de la jornada normal
    public Double getHoras_extra() { return horas_extra; }
    public void setHoras_extra(Double horas_extra) { this.horas_extra = horas_extra; }

    // Especialidad: tipo de trabajo (ELECTRICIDAD o FONTANERIA)
    public especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(especialidad especialidad) { this.especialidad = especialidad; }

    // Indica si el parte fue creado por un gestor/admin en lugar del propio trabajador
    public boolean isCreado_por_gestor() { return creado_por_gestor; }
    public void setCreado_por_gestor(boolean creado_por_gestor) {
        this.creado_por_gestor = creado_por_gestor;
    }

    // URL de la imagen de la firma digital del trabajador (almacenada en Supabase Storage)
    public String getFirma_url() { return firma_url; }
    public void setFirma_url(String firma_url) { this.firma_url = firma_url; }

    // Nombre de la persona que firmo el parte
    public String getNombre_firmado() { return nombre_firmado; }
    public void setNombre_firmado(String nombre_firmado) { this.nombre_firmado = nombre_firmado; }

    // Descripcion de trabajos extra o adicionales realizados
    public String getTrabajos_extra() { return trabajos_extra; }
    public void setTrabajos_extra(String trabajos_extra) { this.trabajos_extra = trabajos_extra; }
}