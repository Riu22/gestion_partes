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

    public void habilitarFechas(String perfilId, List<LocalDate> fechas) {
        UUID uuid = UUID.fromString(perfilId);
        List<FechaPermitida> nuevas = fechas.stream()
                .filter(f -> !repo.existsByPerfilIdAndFecha(uuid, f))
                .map(f -> new FechaPermitida(uuid, f))
                .toList();
        repo.saveAll(nuevas);
    }

    @Transactional
    public void deshabilitarFecha(String perfilId, LocalDate fecha) {
        repo.deleteByPerfilIdAndFecha(UUID.fromString(perfilId), fecha);
    }

    @Transactional
    public void deshabilitarTodas(String perfilId) {
        repo.deleteByPerfilId(UUID.fromString(perfilId));
    }

    public boolean fechaPermitida(String perfilId, LocalDate fecha) {
        return repo.existsByPerfilIdAndFecha(UUID.fromString(perfilId), fecha);
    }

    public List<LocalDate> getFechasDeUsuario(String perfilId) {
        return repo.findByPerfilIdOrderByFechaAsc(UUID.fromString(perfilId))
                .stream()
                .map(FechaPermitida::getFecha)
                .toList();
    }

    public Map<String, List<LocalDate>> getUsuariosActivos() {
        return repo.findAllByOrderByPerfilIdAscFechaAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        f -> f.getPerfilId().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(FechaPermitida::getFecha, Collectors.toList())
                ));
    }

    public boolean puedeUsarFechaLibre(String perfilId, LocalDate fecha) {
        return fechaPermitida(perfilId, fecha);
    }
}