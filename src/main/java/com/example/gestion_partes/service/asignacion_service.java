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

    @Autowired
    perfil_repo perfil_repo;
    @Autowired
    obra_repo obra_repo;
    @Autowired
    asignacion_obra_repo asignacion_obra_repo;

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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este perfil ya está asignado a esta obra");
        }

        return asignacion_obra_repo.save(new asignacion_obra(perfil, obra));
    }

    public List<asignacion_obra> get_asignaciones_obra(Long obraId) {
        if (!obra_repo.existsById(obraId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        return asignacion_obra_repo.findByObraId(obraId);
    }

    public void eliminar_asignacion_obra(Long asignacionId) {
        if (!asignacion_obra_repo.existsById(asignacionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada");
        }
        asignacion_obra_repo.deleteById(asignacionId);
    }

    // --- GESTIÓN DE EQUIPO (SUBORDINADOS) ---

    // Asigna un operario a un encargado
    // Método unificado que reemplaza a asignar_operario_a_encargado y asignar_encargado_a_jefe
    public perfil asignar_subordinado(UUID subordinadoId, UUID jefeId) {
        perfil subordinado = perfil_repo.findById(subordinadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
        perfil jefe = perfil_repo.findById(jefeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jefe no encontrado"));

        // Solo ADMINISTRACION/GESTION no pueden tener jefe directo
        if (subordinado.getRol() == user_rol.ADMINISTRACION || subordinado.getRol() == user_rol.GESTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ADMINISTRACION y GESTION no pueden tener jefe directo");
        }

        subordinado.setJefeDirecto(jefe);
        return perfil_repo.save(subordinado);
    }

    // Asigna un encargado a un jefe de obra
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

    /**
     * MÉTODO NUEVO: Elimina la relación de subordinación.
     * Simplemente pone el jefeDirecto a null.
     */
    public void quitar_jefe_directo(UUID usuarioId) {
        perfil usuario = perfil_repo.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        usuario.setJefeDirecto(null);
        perfil_repo.save(usuario);
    }

    /**
     * Mejora: No lanzamos error si no hay, devolvemos lista vacía para que el Front no explote.
     */
    public List<perfil> get_mis_subordinados(UUID id) {
        return perfil_repo.findByJefeDirecto_Id(id);
    }

    // --- CONSULTAS DE OBRAS ---

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

    public List<asignacion_obra> get_obras_de_perfil(UUID perfilId) {
        return asignacion_obra_repo.findByPerfilId(perfilId);
    }

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

    public List<Long> getObrasDeJefe(UUID jefeId) {
        return get_mis_obras(jefeId)
                .stream()
                .map(a -> a.getObra().getId())
                .collect(Collectors.toList());
    }
}