/*
 * SERVICIO: asignacion_service (Gestion de asignaciones de trabajadores a obras)
 *
 * Gestiona las relaciones entre trabajadores y obras, asi como las
 * relaciones jerarquicas entre los distintos roles (jefe -> encargado -> operario).
 *
 * Metodos principales:
 *
 * ASIGNACIONES A OBRAS:
 * - asignar_a_obra:           Asigna un trabajador a una obra
 * - get_asignaciones_obra:    Obtiene todas las asignaciones de una obra
 * - eliminar_asignacion_obra: Elimina una asignacion concreta
 * - asignar_obras_batch:      Asigna varias obras a un trabajador de golpe
 * - get_obras_de_perfil:      Obras asignadas a un trabajador
 *
 * JERARQUIA (subordinacion):
 * - asignar_subordinado:      Establece la relacion jefe->subordinado
 * - asignar_encargado_a_jefe: Asigna un ENCARGADO como subordinado de un JEFE_DE_OBRA
 * - quitar_jefe_directo:      Elimina la relacion de subordinacion
 * - get_mis_subordinados:     Obtiene los subordinados directos de un usuario
 * - asignar_subordinados_batch: Asigna varios subordinados a un jefe de golpe
 *
 * CONSULTAS:
 * - get_mis_obras:      Obtiene las obras visibles para un usuario segun su rol
 * - getObrasDeJefe:     IDs de las obras de un jefe
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.model.*;
import com.example.gestion_partes.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class asignacion_service {

    @Autowired perfil_repo perfil_repo;
    @Autowired obra_repo obra_repo;
    @Autowired asignacion_obra_repo asignacion_obra_repo;

    // ─── ASIGNACIONES A OBRAS ─────────────────────────────────────────────

    /*
     * Asigna un trabajador a una obra.
     * Recibe: el UUID del trabajador y el ID de la obra
     * Devuelve: el objeto asignacion_obra creado
     *
     * Restricciones:
     * - ADMINISTRACION y GESTION no se asignan a obras (tienen visibilidad global)
     * - No se permite asignar dos veces el mismo trabajador a la misma obra
     */
    public asignacion_obra asignar_a_obra(UUID perfilId, Long obraId) {
        perfil perfil = perfil_repo.findById(perfilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
        obra obra = obra_repo.findById(obraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada"));

        if (perfil.getRol() == user_rol.ADMINISTRACION || perfil.getRol() == user_rol.GESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADMINISTRACION y GESTION tienen visibilidad total");
        }

        if (asignacion_obra_repo.existsByPerfilIdAndObraId(perfilId, obraId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este perfil ya esta asignado a esta obra");
        }

        return asignacion_obra_repo.save(new asignacion_obra(perfil, obra));
    }

    /*
     * Obtiene todas las asignaciones (trabajadores) de una obra concreta.
     * Recibe: el ID de la obra
     * Devuelve: lista de asignaciones
     */
    public List<asignacion_obra> get_asignaciones_obra(Long obraId) {
        if (!obra_repo.existsById(obraId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        return asignacion_obra_repo.findByObraId(obraId);
    }

    /*
     * Elimina una asignacion concreta.
     * Recibe: el ID de la asignacion a eliminar
     */
    public void eliminar_asignacion_obra(Long asignacionId) {
        if (!asignacion_obra_repo.existsById(asignacionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignacion no encontrada");
        }
        asignacion_obra_repo.deleteById(asignacionId);
    }

    // ─── GESTION DE EQUIPO (SUBORDINADOS) ────────────────────────────────

    /*
     * Asigna un subordinado a un jefe (establece la relacion jerarquica).
     * Sirve tanto para asignar OPERARIO a ENCARGADO como ENCARGADO a JEFE_DE_OBRA.
     *
     * Recibe: el UUID del subordinado y el UUID del jefe
     * Devuelve: el perfil del subordinado actualizado
     *
     * ADMINISTRACION y GESTION no pueden tener jefe directo.
     */
    public perfil asignar_subordinado(UUID subordinadoId, UUID jefeId) {
        perfil subordinado = perfil_repo.findById(subordinadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
        perfil jefe = perfil_repo.findById(jefeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jefe no encontrado"));

        if (subordinado.getRol() == user_rol.ADMINISTRACION || subordinado.getRol() == user_rol.GESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADMINISTRACION y GESTION no pueden tener jefe directo");
        }

        subordinado.setJefeDirecto(jefe);
        return perfil_repo.save(subordinado);
    }

    /*
     * Asigna un ENCARGADO como subordinado de un JEFE_DE_OBRA.
     * Recibe: UUID del encargado y UUID del jefe de obra
     * Devuelve: el encargado actualizado con su nuevo jefe
     */
    public perfil asignar_encargado_a_jefe(UUID encargadoId, UUID jefeId) {
        perfil encargado = perfil_repo.findById(encargadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encargado no encontrado"));
        perfil jefe = perfil_repo.findById(jefeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jefe de obra no encontrado"));

        if (encargado.getRol() != user_rol.ENCARGADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El perfil no es ENCARGADO");
        }
        encargado.setJefeDirecto(jefe);
        return perfil_repo.save(encargado);
    }

    /*
     * Elimina la relacion de subordinacion de un usuario.
     * Simplemente pone su jefeDirecto a null (se queda sin jefe asignado).
     * Recibe: el UUID del usuario
     */
    public void quitar_jefe_directo(UUID usuarioId) {
        perfil usuario = perfil_repo.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        usuario.setJefeDirecto(null);
        perfil_repo.save(usuario);
    }

    /*
     * Obtiene los subordinados directos de un usuario.
     * Recibe: el UUID del jefe
     * Devuelve: lista de perfiles que tienen a ese usuario como jefeDirecto
     * Si no tiene subordinados, devuelve lista vacia.
     */
    public List<perfil> get_mis_subordinados(UUID id) {
        return perfil_repo.findByJefeDirecto_Id(id);
    }

    // ─── CONSULTAS DE OBRAS ──────────────────────────────────────────────

    /*
     * Obtiene las obras visibles para un usuario segun su rol:
     * - JEFE_DE_OBRA o ENCARGADO: devuelve las obras a las que esta asignado
     * - OPERARIO: devuelve las obras del encargado al que reporta
     * - Otros (ADMIN, GESTION): lista vacia (tienen visibilidad global)
     *
     * Recibe: el UUID del usuario
     * Devuelve: lista de asignaciones (cada una contiene obra + perfil)
     */
    public List<asignacion_obra> get_mis_obras(UUID id) {
        perfil usuario = perfil_repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.JEFE_DE_OBRA || usuario.getRol() == user_rol.ENCARGADO) {
            return asignacion_obra_repo.findByPerfilId(id);
        }

        if (usuario.getRol() == user_rol.OPERARIO) {
            return asignacion_obra_repo.findObrasDeEncargadoDeOperario(id);
        }

        return List.of();
    }

    /*
     * Obtiene todas las obras asignadas a un perfil (sin logica de roles).
     * Recibe: el UUID del perfil
     * Devuelve: lista de asignaciones
     */
    public List<asignacion_obra> get_obras_de_perfil(UUID perfilId) {
        return asignacion_obra_repo.findByPerfilId(perfilId);
    }

    /*
     * Asigna varias obras a un trabajador de una sola vez (asignacion masiva).
     * Recibe: el UUID del trabajador y una lista de IDs de obras
     * Solo se anaden las obras que no esten ya asignadas (evita duplicados).
     */
    public void asignar_obras_batch(UUID perfilId, List<Long> obraIds) {
        perfil perfil = perfil_repo.findById(perfilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (perfil.getRol() == user_rol.ADMINISTRACION || perfil.getRol() == user_rol.GESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADMINISTRACION y GESTION tienen visibilidad total");
        }

        List<asignacion_obra> nuevas = obraIds.stream()
                .filter(obraId -> !asignacion_obra_repo.existsByPerfilIdAndObraId(perfilId, obraId))
                .map(obraId -> obra_repo.findById(obraId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Obra no encontrada: " + obraId)))
                .map(obra -> new asignacion_obra(perfil, obra))
                .toList();

        asignacion_obra_repo.saveAll(nuevas);
    }

    /*
     * Asigna varios subordinados a un jefe de una sola vez.
     * Recibe: el UUID del jefe y una lista de UUIDs de subordinados
     * Filtra automaticamente los perfiles ADMINISTRACION y GESTION
     * (que no pueden tener jefe).
     */
    public void asignar_subordinados_batch(UUID jefeId, List<UUID> subordinadoIds) {
        perfil jefe = perfil_repo.findById(jefeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jefe no encontrado"));

        List<perfil> subordinados = subordinadoIds.stream()
                .map(id -> perfil_repo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Perfil no encontrado: " + id)))
                .filter(p -> p.getRol() != user_rol.ADMINISTRACION && p.getRol() != user_rol.GESTION)
                .toList();

        for (perfil sub : subordinados) {
            sub.setJefeDirecto(jefe);
        }
        perfil_repo.saveAll(subordinados);
    }

    /*
     * Obtiene los IDs de las obras de un jefe (util para filtrar informes).
     * Recibe: el UUID del jefe
     * Devuelve: lista de IDs de obras
     */
    public List<Long> getObrasDeJefe(UUID jefeId) {
        return get_mis_obras(jefeId)
                .stream()
                .map(a -> a.getObra().getId())
                .collect(Collectors.toList());
    }
}