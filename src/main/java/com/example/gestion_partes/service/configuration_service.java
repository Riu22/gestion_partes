package com.example.gestion_partes.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class configuration_service {

    // Map<userId, Set<fechas permitidas>>
    private final Map<String, Set<LocalDate>> fechasPermitidas = new ConcurrentHashMap<>();

    // Añade fechas sueltas a un usuario (no reemplaza, acumula)
    public void habilitarFechas(String id, List<LocalDate> fechas) {
        fechasPermitidas.computeIfAbsent(id, k -> new HashSet<>()).addAll(fechas);
    }

    // Quita una fecha suelta concreta
    public void deshabilitarFecha(String id, LocalDate fecha) {
        Set<LocalDate> set = fechasPermitidas.get(id);
        if (set != null) {
            set.remove(fecha);
            if (set.isEmpty()) fechasPermitidas.remove(id);
        }
    }

    // Quita todas las fechas de un usuario
    public void deshabilitarTodas(String id) {
        fechasPermitidas.remove(id);
    }

    // Comprueba si una fecha concreta está permitida para ese usuario
    public boolean fechaPermitida(String id, LocalDate fecha) {
        Set<LocalDate> set = fechasPermitidas.get(id);
        if (set == null) return false;
        return set.contains(fecha);
    }

    // Devuelve todas las fechas permitidas de un usuario
    public Set<LocalDate> getFechasDeUsuario(String id) {
        return fechasPermitidas.getOrDefault(id, Set.of());
    }

    // Para listar desde el front — Map<userId, List<fecha>>
    public Map<String, List<LocalDate>> getUsuariosActivos() {
        return fechasPermitidas.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().sorted().toList()
                ));
    }

    public boolean puedeUsarFechaLibre(String id, LocalDate fecha) { // Added LocalDate fecha
        Set<LocalDate> fechas = fechasPermitidas.get(id);
        return fechas != null && fechas.contains(fecha);
    }
}