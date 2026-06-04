package com.example.gestion_partes.repo;

import com.example.gestion_partes.model.perfil;
import com.example.gestion_partes.model.user_rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface perfil_repo extends JpaRepository<perfil, UUID> {

    // ── Métodos existentes (sin tocar) ────────────────────────────────────────

    List<perfil> findAllByOrderByActivoDescApellidosAscNameAsc();
    List<perfil> findByJefeDirecto_Id(UUID jefeId);
    List<perfil> findByActivoTrueAndRolIn(List<user_rol> roles);
    List<perfil> findByActivoTrue();
    Optional<perfil> findByEmail(String username);

    // ── Métodos optimizados ───────────────────────────────────────────────────

    /**
     * Sustituye el bucle N+1 de findByJefeDirecto_Id().
     *
     * Devuelve en una sola query todos los subordinados directos del jefe
     * MÁS los subordinados de sus encargados (dos niveles), igual que la
     * lógica Java anterior pero sin roundtrips adicionales.
     *
     * Usa CTE recursivo de PostgreSQL. Si se usa H2 en tests, ver MIGRACION.md.
     */
    @Query(value = """
            WITH RECURSIVE subordinados AS (
                SELECT id, email, nombre, apellidos, codigo, rol,
                       jefe_directo_id, activo, postventa,
                       especialidad, grupo_profesional, creado_el
                FROM perfiles
                WHERE jefe_directo_id = :jefeId

                UNION ALL

                SELECT p.id, p.email, p.nombre, p.apellidos, p.codigo, p.rol,
                       p.jefe_directo_id, p.activo, p.postventa,
                       p.especialidad, p.grupo_profesional, p.creado_el
                FROM perfiles p
                INNER JOIN subordinados s ON p.jefe_directo_id = s.id
                WHERE s.rol = 'ENCARGADO'
            )
            SELECT * FROM subordinados
            """, nativeQuery = true)
    List<perfil> findSubordinadosDosNiveles(@Param("jefeId") UUID jefeId);

    /**
     * Carga solo los perfiles cuyo código está en la colección dada.
     * Reemplaza findAll() + filtrado en memoria.
     *
     * Precondición: no llamar con colección vacía (genera IN () inválido).
     */
    @Query("SELECT p FROM perfil p WHERE p.codigo IN :codigos")
    List<perfil> findByCodigos(@Param("codigos") Collection<String> codigos);

    /**
     * Un solo round-trip que cubre los tres casos de contabilidad_service:
     *  - perfiles que aparecen en partes (por código)
     *  - subordinados directos del jefe (por id)
     *  - todos los activos OPERARIO/ENCARGADO (cuando incluirTodos = true)
     *
     * IMPORTANTE: no llamar con codigos o ids vacíos; usar centinelas
     * ("__VACIO__" / UUID cero) para evitar IN () inválido en SQL.
     */
    @Query("""
        SELECT p FROM perfil p
        WHERE p.activo = true
        AND (
            (:incluirTodos = true AND p.rol IN :roles)
            OR p.codigo IN :codigos
            OR p.id     IN :ids
        )
    """)
    List<perfil> findParaContabilidad(
            @Param("codigos")      Collection<String>  codigos,
            @Param("ids")          Collection<UUID>    ids,
            @Param("roles")        List<user_rol>      roles,
            @Param("incluirTodos") boolean             incluirTodos
    );
}