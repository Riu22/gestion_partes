/*
 * SERVICIO: configuration_service (Gestion de fechas habilitadas para edicion)
 *
 * Este servicio permite a los administradores habilitar fechas pasadas
 * concretas para que los trabajadores puedan crear o modificar partes
 * de trabajo de forma retroactiva (mas alla del limite de 14 dias).
 *
 * Metodos principales:
 * - habilitarFechas:       Concede permiso a un trabajador para editar una o
 *                          varias fechas pasadas concretas
 * - deshabilitarFecha:     Revoca el permiso para una fecha concreta
 * - deshabilitarTodas:     Revoca todos los permisos de un trabajador
 * - fechaPermitida:        Consulta si un trabajador tiene permiso para una fecha
 * - getFechasDeUsuario:    Obtiene todas las fechas habilitadas de un trabajador
 * - getUsuariosActivos:    Obtiene un mapa de todos los trabajadores con sus
 *                          fechas habilitadas
 * - puedeUsarFechaLibre:   Alias de fechaPermitida, para uso en validaciones
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.model.FechaPermitida;
import com.example.gestion_partes.repo.FechaPermitidaRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class configuration_service {

    @Autowired
    FechaPermitidaRepo repo;

    /*
     * Habilita una o varias fechas para que un trabajador pueda editar
     * partes de trabajo de forma retroactiva.
     *
     * Recibe: el ID del trabajador (como String) y una lista de fechas
     * Devuelve: void (guarda en base de datos)
     *
     * Solo se anaden las fechas que NO tengan ya un permiso concedido
     * (evita duplicados).
     */
    public void habilitarFechas(String perfilId, List<LocalDate> fechas) {
        UUID uuid = UUID.fromString(perfilId);
        List<FechaPermitida> nuevas = fechas.stream()
                .filter(f -> !repo.existsByPerfilIdAndFecha(uuid, f))
                .map(f -> new FechaPermitida(uuid, f))
                .toList();
        repo.saveAll(nuevas);
    }

    /*
     * Revoca el permiso de edicion para una fecha concreta de un trabajador.
     *
     * Recibe: el ID del trabajador (como String) y la fecha a deshabilitar
     * Devuelve: void
     */
    @Transactional
    public void deshabilitarFecha(String perfilId, LocalDate fecha) {
        repo.deleteByPerfilIdAndFecha(UUID.fromString(perfilId), fecha);
    }

    /*
     * Revoca TODOS los permisos de edicion de un trabajador.
     *
     * Recibe: el ID del trabajador (como String)
     * Devuelve: void
     */
    @Transactional
    public void deshabilitarTodas(String perfilId) {
        repo.deleteByPerfilId(UUID.fromString(perfilId));
    }

    /*
     * Comprueba si un trabajador tiene permiso para editar una fecha concreta.
     *
     * Recibe: el ID del trabajador (como String) y la fecha a consultar
     * Devuelve: true si tiene permiso, false si no
     */
    public boolean fechaPermitida(String perfilId, LocalDate fecha) {
        return repo.existsByPerfilIdAndFecha(UUID.fromString(perfilId), fecha);
    }

    /*
     * Obtiene todas las fechas habilitadas para un trabajador concreto.
     *
     * Recibe: el ID del trabajador (como String)
     * Devuelve: lista de fechas habilitadas, ordenadas cronologicamente
     */
    public List<LocalDate> getFechasDeUsuario(String perfilId) {
        return repo.findByPerfilIdOrderByFechaAsc(UUID.fromString(perfilId))
                .stream()
                .map(FechaPermitida::getFecha)
                .toList();
    }

    /*
     * Obtiene todas las configuraciones activas (todos los trabajadores
     * con sus fechas habilitadas).
     *
     * Devuelve: un mapa donde las claves son los IDs de los trabajadores
     * y los valores son las listas de fechas habilitadas para cada uno
     */
    public Map<String, List<LocalDate>> getUsuariosActivos() {
        return repo.findAllByOrderByPerfilIdAscFechaAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        f -> f.getPerfilId().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(FechaPermitida::getFecha, Collectors.toList())
                ));
    }

    /*
     * Metodo de consulta (alias de fechaPermitida).
     * Recibe: ID del trabajador y fecha
     * Devuelve: true si tiene permiso
     */
    public boolean puedeUsarFechaLibre(String perfilId, LocalDate fecha) {
        return fechaPermitida(perfilId, fecha);
    }
}