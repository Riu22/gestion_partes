package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.create_user_dto;
import com.example.gestion_partes.dto.update_user_dto;
import com.example.gestion_partes.repo.perfil_repo;
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

    public perfil update_profile(UUID id, update_user_dto datosNuevos) {
        perfil perfilExistente = user_repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Solo actualiza si el dato viene en el JSON
        if (datosNuevos.name() != null) {
            perfilExistente.setName(datosNuevos.name());
        }

        if (datosNuevos.rol() != null) {
            perfilExistente.setRol(datosNuevos.rol());
        }

        // Aquí está el truco del Boolean: si no viene, no cambia el estado actual
        if (datosNuevos.activo() != null) {
            perfilExistente.setActivo(datosNuevos.activo());
        }

        return user_repo.save(perfilExistente);
    }
    public void create_user(create_user_dto new_user) {
        String url = supabase_url + "/auth/v1/admin/users";
        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        // 2. Preparamos el cuerpo (Lo que Supabase espera)
        Map<String, Object> body = new HashMap<>();
        body.put("email", new_user.email());
        body.put("password", new_user.password());
        body.put("email_confirm", true); // Para que no tenga que confirmar email en local

        // Metemos el ROL en los metadatos para que el Trigger de SQL lo lea
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("rol", new_user.rol().toString());
        metadata.put("full_name", new_user.name());
        body.put("user_metadata", metadata);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // 3. Enviamos la petición
        try {
            rest_template.postForEntity(url, request, String.class);
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