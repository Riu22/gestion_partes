/* Conversor personalizado de JWT a AuthenticationToken de Spring Security.
   Extrae el rol del usuario desde los claims app_metadata o user_metadata del JWT de Supabase
   y lo convierte en una autoridad ROLE_* para que Spring Security pueda evaluar @PreAuthorize. */
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

    /* Convierte un JWT en un JwtAuthenticationToken con las autoridades (roles) del usuario.
       Busca el claim "rol" primero en app_metadata y luego en user_metadata (por si Supabase lo guarda en uno u otro). */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        String rol = null;

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
