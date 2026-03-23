package com.example.gestion_partes.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Component
public class CustomJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 1. Extraemos los metadatos del usuario del token de Supabase
        Map<String, Object> userMetadata = jwt.getClaimAsMap("user_metadata");

        if (userMetadata != null && userMetadata.containsKey("rol")) {
            String rol = (String) userMetadata.get("rol");

            // 2. IMPORTANTE: Le añadimos "ROLE_" para que Spring lo entienda como un rol
            // Si en la DB es "ADMINISTRACION", aquí será "ROLE_ADMINISTRACION"
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
        }

        // 3. Devolvemos el token con las autoridades cargadas
        return new JwtAuthenticationToken(jwt, authorities);
    }
}