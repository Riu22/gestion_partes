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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (datosNuevos.name() != null) perfilExistente.setName(datosNuevos.name());
        if (datosNuevos.rol() != null) perfilExistente.setRol(datosNuevos.rol());
        if (datosNuevos.activo() != null) perfilExistente.setActivo(datosNuevos.activo());
        if (datosNuevos.codigo() != null) perfilExistente.setCodigo(datosNuevos.codigo());
        if (datosNuevos.postventa() != null) {
            perfilExistente.setPostventa(datosNuevos.postventa());
        }
        return user_repo.save(perfilExistente);
    }

    public void create_user(create_user_dto new_user) {
        String url = supabase_url + "/auth/v1/admin/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        Map<String, Object> body = new HashMap<>();
        body.put("email", new_user.email());
        body.put("password", new_user.password());
        body.put("email_confirm", true);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("rol", new_user.rol().toString());
        metadata.put("full_name", new_user.name());
        body.put("user_metadata", metadata);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            rest_template.postForEntity(url, request, String.class);

            // Thread.sleep fuera del lambda para evitar InterruptedException
            if (new_user.codigo() != null || new_user.postventa() != null) {
                Thread.sleep(500);
                user_repo.findByEmail(new_user.email()).ifPresent(p -> {
                    if (new_user.codigo() != null) p.setCodigo(new_user.codigo());
                    if (new_user.postventa() != null) p.setPostventa(new_user.postventa());
                    user_repo.save(p);
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Hilo interrumpido al crear usuario: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error al crear usuario: " + e.getMessage());
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