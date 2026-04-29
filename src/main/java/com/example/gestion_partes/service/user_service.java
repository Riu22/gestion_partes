package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.create_user_dto;
import com.example.gestion_partes.dto.update_user_dto;
import com.example.gestion_partes.repo.perfil_repo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.example.gestion_partes.model.perfil;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class user_service {
    @Value("${supabase.url}")
    private String supabase_url;

    @Value("${supabase.service.key}")
    private String service_key;

    @Autowired
    private RestTemplate rest_template;

    @Autowired
    perfil_repo user_repo;

    @Transactional
    public perfil update_profile(UUID id, update_user_dto datosNuevos) {
        perfil perfilExistente = user_repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (datosNuevos.name() != null) perfilExistente.setName(datosNuevos.name());
        if (datosNuevos.apellidos() != null) perfilExistente.setApellidos(datosNuevos.apellidos());
        if (datosNuevos.rol() != null) perfilExistente.setRol(datosNuevos.rol());
        if (datosNuevos.activo() != null) perfilExistente.setActivo(datosNuevos.activo());
        if (datosNuevos.codigo() != null) perfilExistente.setCodigo(datosNuevos.codigo());
        if (datosNuevos.postventa() != null) perfilExistente.setPostventa(datosNuevos.postventa());
        if (datosNuevos.grupo_profesional() != null) perfilExistente.setGrupo_profesional(datosNuevos.grupo_profesional());

        return user_repo.save(perfilExistente);
    }

    public void create_user(create_user_dto new_user) {
        String url = supabase_url + "/auth/v1/admin/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        // 1. Preparar metadatos (Enviamos TODO al trigger de la base de datos)
        Map<String, Object> metadata = new HashMap<>();

        // Fíjate que ahora usamos "nombre" y "apellidos" para que coincida con el Trigger
        metadata.put("nombre", new_user.name());
        metadata.put("apellidos", new_user.apellidos());
        metadata.put("rol", new_user.rol().toString());

        // Forzamos mayúsculas para la especialidad
        String especialidadStr = (new_user.especialidad() != null)
                ? new_user.especialidad().name().toUpperCase()
                : "ELECTRICIDAD";
        metadata.put("especialidad", especialidadStr);

        // Añadimos el resto de campos si existen
        if (new_user.codigo() != null) {
            metadata.put("codigo", new_user.codigo());
        }
        if (new_user.postventa() != null) {
            metadata.put("postventa", new_user.postventa());
        }
        if (new_user.grupo_profesional() != null && !new_user.grupo_profesional().isBlank()) {
            metadata.put("grupo_profesional", new_user.grupo_profesional());
        }

        // 2. Construir el cuerpo de la petición
        Map<String, Object> body = new HashMap<>();
        body.put("email", new_user.email());
        body.put("password", new_user.password());
        body.put("email_confirm", true);
        body.put("user_metadata", metadata); // Todo va aquí dentro

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            // 3. Enviar a Supabase Auth.
            // Esto crea el usuario y dispara el Trigger automáticamente.
            rest_template.postForEntity(url, request, String.class);

            // ¡Listo! Ya no necesitamos Thread.sleep() ni user_repo.save()
            // La creación es atómica e inmediata.

        } catch (Exception e) {
            throw new RuntimeException("Error al crear usuario en Supabase: " + e.getMessage());
        }
    }

    public void delete_user(UUID id) {
        String url = supabase_url + "/auth/v1/admin/users/" + id;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            rest_template.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error al borrar usuario en Supabase: " + e.getMessage());
        }
    }
}