/* Configuración de seguridad Spring Security para la aplicación.
   Configura CORS, deshabilita CSRF (API REST stateless), define rutas públicas
   y configura el servidor de recursos OAuth2 con JWT validado por clave HMAC-SHA256.
   Los JWT son emitidos por Supabase Auth y se decodifican con un secreto compartido. */
package com.example.gestion_partes.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class security_config {

    /* Secreto JWT configurado en application.properties (debe coincidir con el de Supabase). */
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private CustomJwtConverter customJwtConverter;

    /* Define la cadena de filtros de seguridad: CORS, sin CSRF, stateless, autorización por JWT.
       Las rutas de Swagger, versión y prueba son públicas; todo lo demás requiere autenticación. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/version",
                                "/api/v1/prueba/**"

                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(customJwtConverter)
                                .decoder(jwtDecoder())
                        )
                );
        return http.build();
    }

    /* Configura CORS para permitir peticiones desde cualquier origen (patrón "*"),
       métodos HTTP estándar y cualquier cabecera. No permite credenciales (cookies). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /* Crea un decodificador JWT que valida el token con HMAC-SHA256 usando el secreto compartido.
       Solo valida la expiración (timestamp), no el issuer, porque Supabase local no lo envía siempre. */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(validators);

        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }
}
