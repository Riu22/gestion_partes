package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.partes_dto;
import com.example.gestion_partes.model.obra;
import com.example.gestion_partes.model.partes_trabajo;
import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import com.example.gestion_partes.repo.obra_repo;
import com.example.gestion_partes.repo.partes_trabajo_repo;
import com.example.gestion_partes.repo.perfil_repo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class partes_service {
    @Autowired partes_trabajo_repo partes_trabajo_repo;
    @Autowired perfil_repo perfil_repo;
    @Autowired obra_repo obra_repo;

    public partes_trabajo create_parte(partes_dto dto, String sub) {
        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        // Validar límite de 2 semanas
        LocalDate limiteMinimo = LocalDate.now().minusWeeks(2);
        if (dto.fecha().isBefore(limiteMinimo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes crear partes con más de 2 semanas de antigüedad");
        }

        // Solo GESTION/ADMIN pueden crear partes para otros
        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;
        if (!esGestor && !solicitante.getId().equals(dto.id_perfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes crear partes para ti mismo");
        }

        // JEFE_DE_OBRA no usa este endpoint
        if (solicitante.getRol() == user_rol.JEFE_DE_OBRA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los jefes de obra deben usar el endpoint de partes por porcentaje");
        }

        obra obra = obra_repo.findById(dto.id_obra())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Obra no encontrada"));
        perfil perfil = perfil_repo.findById(dto.id_perfil())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));
        if (!obra.isActiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear partes en una obra inactiva");
        }
        partes_trabajo nuevo = new partes_trabajo();
        nuevo.setObra(obra);
        nuevo.setPerfil(perfil);
        nuevo.setFecha(dto.fecha());
        nuevo.setDescripcion(dto.descripcion());
        nuevo.setHoras_normales(dto.horas_normales() != null ? dto.horas_normales() : 8.0);
        nuevo.setHoras_normales(dto.horas_normales() != null ? dto.horas_normales() : 8.0);
        nuevo.setHoras_extra(0.0); // siempre 0, se elimina del formulario
        nuevo.setEspecialidad(dto.especialidad());

        return partes_trabajo_repo.save(nuevo);
    }

    public List<partes_trabajo> get_partes_jerarquico(String sub) {
        perfil usuario = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        if (usuario.getRol() == user_rol.ADMINISTRACION
                || usuario.getRol() == user_rol.GESTION) {
            return partes_trabajo_repo.findAll();
        }

        // Partes propios (siempre incluidos)
        List<partes_trabajo> resultado = new ArrayList<>(
                partes_trabajo_repo.findByPerfilId(usuario.getId())
        );

        if (usuario.getRol() == user_rol.JEFE_DE_OBRA) {
            resultado.addAll(partes_trabajo_repo.findPartesParaJefeObra(usuario.getId()));
        } else if (usuario.getRol() == user_rol.ENCARGADO) {
            resultado.addAll(partes_trabajo_repo.findPartesParaEncargado(usuario.getId()));
        }

        return resultado.stream()
                .collect(Collectors.toMap(partes_trabajo::getId, p -> p, (a, b) -> a))
                .values()
                .stream()
                .toList();
    }


    public void delete_parte(Long parteId) {
        if (!partes_trabajo_repo.existsById(parteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parte no encontrado");
        }
        partes_trabajo_repo.deleteById(parteId);
    }
    public partes_trabajo update_parte(Long parteId, partes_dto dto, String sub) {
        partes_trabajo parte = partes_trabajo_repo.findById(parteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parte no encontrado"));

        perfil solicitante = perfil_repo.findById(UUID.fromString(sub))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Perfil no encontrado"));

        // Solo el propio usuario o gestión/admin pueden editar
        boolean esGestor = solicitante.getRol() == user_rol.ADMINISTRACION
                || solicitante.getRol() == user_rol.GESTION;
        if (!esGestor && !parte.getPerfil().getId().equals(solicitante.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes editar partes de otros usuarios");
        }

        // Validar límite de 2 semanas
        LocalDate limiteMinimo = LocalDate.now().minusWeeks(2);
        if (parte.getFecha().isBefore(limiteMinimo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes editar partes con más de 2 semanas de antigüedad");
        }

        if (dto.id_obra() != null) {
            obra obra = obra_repo.findById(dto.id_obra())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Obra no encontrada"));
            parte.setObra(obra);
        }
        if (dto.fecha() != null) parte.setFecha(dto.fecha());
        if (dto.horas_normales() != null) parte.setHoras_normales(dto.horas_normales());
        if (dto.descripcion() != null) parte.setDescripcion(dto.descripcion());
        if (dto.especialidad() != null) parte.setEspecialidad(dto.especialidad());

        return partes_trabajo_repo.save(parte);
    }
}