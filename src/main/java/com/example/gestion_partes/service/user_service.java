/*
 * SERVICIO: user_service (Gestion de usuarios)
 *
 * Proporciona la logica para crear, modificar y eliminar usuarios,
 * tanto en la base de datos local (tabla perfiles) como en el
 * sistema de autenticacion externo de Supabase Auth.
 *
 * Metodos:
 * - update_profile:  Modifica los datos de un perfil existente (nombre, rol, etc.)
 *                    y opcionalmente actualiza email/contrasena en Supabase Auth
 * - create_user:     Crea un nuevo usuario en Supabase Auth con sus metadatos,
 *                    lo que automaticamente crea el perfil en la BD local
 * - delete_user:     Elimina un usuario de Supabase Auth (y por tanto su perfil)
 *
 * Supabase es un servicio externo que proporciona autenticacion y base de datos.
 * Este servicio se comunica con la API de administracion de Supabase usando
 * una clave de servicio (service key) que tiene permisos de administrador.
 */
package com.example.gestion_partes.service;

import com.example.gestion_partes.dto.create_user_dto;
import com.example.gestion_partes.dto.update_user_dto;
import com.example.gestion_partes.model.especialidad;
import com.example.gestion_partes.repo.perfil_repo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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

    /*
     * Actualiza los datos de un perfil de usuario existente.
     *
     * Recibe:
     * - id: el UUID del usuario a modificar
     * - datosNuevos: objeto con los campos que se quieren cambiar
     *   (todos son opcionales, solo se actualizan los que no sean null)
     *
     * Devuelve: el perfil actualizado y guardado en base de datos
     *
     * Si ademas se especifica un nuevo email o contrasena, tambien se
     * actualiza en Supabase Auth (el sistema de autenticacion externo).
     */
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
        if (datosNuevos.email() != null) perfilExistente.setEmail(datosNuevos.email()); // <-- FIX
        if (datosNuevos.especialidad() != null) {
            perfilExistente.setEspecialidad(
                    especialidad.valueOf(datosNuevos.especialidad().toUpperCase())
            );
        }
        if (datosNuevos.grupo_profesional() != null)
            perfilExistente.setGrupo_profesional(datosNuevos.grupo_profesional());

        perfilExistente.setCreadoEl(OffsetDateTime.now());
        perfil saved = user_repo.save(perfilExistente);

        if (datosNuevos.email() != null || datosNuevos.password() != null) {
            update_auth_user(id, datosNuevos.email(), datosNuevos.password());
        }

        return saved;
    }

    /*
     * Metodo privado que actualiza el email y/o contrasena de un usuario
     * en el sistema de autenticacion de Supabase (Auth).
     *
     * Recibe: el UUID del usuario, el nuevo email (opcional) y la nueva contrasena (opcional)
     * Devuelve: void
     *
     * Se comunica con la API REST de administracion de Supabase usando
     * la clave de servicio (service_key).
     */
    private void update_auth_user(UUID id, String email, String password) {
        String url = supabase_url + "/auth/v1/admin/users/" + id;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        Map<String, Object> body = new HashMap<>();
        if (email != null && !email.isBlank())       body.put("email", email);
        if (password != null && !password.isBlank()) body.put("password", password);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            rest_template.exchange(url, org.springframework.http.HttpMethod.PUT, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar auth en Supabase: " + e.getMessage());
        }
    }

    /*
     * Crea un nuevo usuario en el sistema.
     *
     * Recibe: un objeto create_user_dto con todos los datos del nuevo usuario
     * Devuelve: void
     *
     * El proceso es:
     * 1. Se llama a la API de administracion de Supabase Auth para crear el usuario
     * 2. Se envian los metadatos (nombre, rol, especialidad, etc.) para que Supabase
     *    los almacene y luego se propaguen a la tabla perfiles
     * 3. El email se confirma automaticamente (email_confirm = true)
     */
    public void create_user(create_user_dto new_user) {
        String url = supabase_url + "/auth/v1/admin/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", service_key);
        headers.set("Authorization", "Bearer " + service_key);

        // Construir los metadatos del usuario (se almacenan en Supabase y se copian a perfiles)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nombre", new_user.name());
        metadata.put("apellidos", new_user.apellidos());
        metadata.put("rol", new_user.rol().toString());

        String especialidadStr = (new_user.especialidad() != null)
                ? new_user.especialidad().name().toUpperCase()
                : "ELECTRICIDAD";
        metadata.put("especialidad", especialidadStr);

        if (new_user.codigo() != null) metadata.put("codigo", new_user.codigo());
        if (new_user.postventa() != null) metadata.put("postventa", new_user.postventa());
        if (new_user.grupo_profesional() != null && !new_user.grupo_profesional().isBlank()) {
            metadata.put("grupo_profesional", new_user.grupo_profesional());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("email", new_user.email());
        body.put("password", new_user.password());
        body.put("email_confirm", true);
        body.put("user_metadata", metadata);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            rest_template.postForEntity(url, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error al crear usuario en Supabase: " + e.getMessage());
        }
    }

    /*
     * Elimina un usuario del sistema.
     *
     * Recibe: el UUID del usuario a eliminar
     * Devuelve: void
     *
     * La eliminacion se hace a traves de la API de administracion de Supabase,
     * que borra el usuario de Auth y automaticamente se elimina el perfil
     * de la tabla perfiles.
     */
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