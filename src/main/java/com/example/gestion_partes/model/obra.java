package com.example.gestion_partes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

/*
 * ENTIDAD: obra (Obras o proyectos de la empresa)
 *
 * Esta clase representa la tabla "obras" de la base de datos.
 * Contiene los datos de cada obra o proyecto en el que trabaja
 * la empresa. Una obra es, por ejemplo, la instalacion electrica
 * de un edificio nuevo, una reforma en un hospital, etc.
 *
 * Los trabajadores registran sus horas de trabajo asociadas a
 * una obra concreta. Cada obra tiene una direccion, un codigo
 * interno de la empresa y un estado (activa o inactiva).
 *
 * Campos principales:
 * - id:         Identificador numerico unico (se genera automaticamente)
 * - nombre:     Nombre de la obra (ej: "Reforma Hotel Miramar")
 * - ubicacion:  Direccion de la obra
 * - municipio:  Municipio donde se encuentra
 * - poblacion:  Poblacion donde se encuentra
 * - codigo:     Codigo interno de la empresa para identificar la obra
 * - activa:     Indica si la obra esta en curso (true) o finalizada (false)
 */
@Entity
@Table(name = "obras", schema = "public")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class obra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String nombre;
    @Column(nullable = false)
    String ubicacion;
    @Column(nullable = false)
    String municipio;
    @Column(nullable = false)
    String poblacion;

    @Column(nullable = false)

    String codigo;
    boolean activa;

    @Column(nullable = false, columnDefinition = "boolean default false")
    Boolean postventa;

    // Constructor vacio requerido por JPA (Hibernate) para crear objetos
    public obra() {
    }

    /*
     * Constructor con todos los campos para crear una obra completa
     * Recibe: nombre, direccion, municipio, poblacion, codigo interno y si esta activa
     */
    public obra (String nombre, String direccion, String municipio, String poblacion,String codigo,Boolean activa,Boolean postventa){
        this.nombre = nombre;
        this.ubicacion = direccion;
        this.municipio = municipio;
        this.poblacion = poblacion;
        this.codigo = codigo;
        this.activa = activa;
        this.postventa = postventa; // Por defecto, una obra nueva no es de postventa
    }

    /*
     * GETTERS Y SETTERS
     *
     * Los metodos "get..." devuelven el valor del campo correspondiente.
     * Los metodos "set..." permiten modificar el valor del campo.
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }
    public String getPoblacion() { return poblacion; }
    public void setPoblacion(String poblacion) { this.poblacion = poblacion; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Boolean isPostventa() {
        return postventa;
    }

    public void setPostventa(Boolean postventa) {
        this.postventa = postventa;
    }
}
