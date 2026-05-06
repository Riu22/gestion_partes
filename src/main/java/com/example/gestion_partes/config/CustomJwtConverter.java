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

        String rol = null;

        // Supabase puede poner el rol en app_metadata o user_metadata
        Map<String, Object> appMetadata = jwt.getClaimAsMap("app_metadata");
        if (appMetadata != null && appMetadata.containsKey("rol")) {
            rol = (String) appMetadata.get("rol");
        }

        if (rol == null) {
            Map<String, Object> userMetadata = jwt.getClaimAsMap("user_metadata");
            if (userMetadata != null && userMetadata.containsKey("rol")) {
                rol = (String) userMetadata.get("rol");
            }
        }

        if (rol != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
        }

        return new JwtAuthenticationToken(jwt, authorities);
    }
}