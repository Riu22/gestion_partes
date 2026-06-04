/*
 * REPOSITORIO: perfil_repo (Acceso a base de datos de perfiles de usuario)
 *
 * Esta interfaz proporciona los metodos para consultar y manipular
 * la tabla "perfiles" en la base de datos. Spring Data JPA genera
 * automaticamente el codigo SQL necesario para cada metodo.
 *
 * Metodos basicos (generados automaticamente por Spring):
 * - findAllByOrderByActivoDescApellidosAscNameAsc: Todos los perfiles ordenados
 *   (primero los activos, luego por apellidos y nombre alfabeticamente)
 * - findByJefeDirecto_Id: Busca los subordinados directos de un jefe
 * - findByActivoTrueAndRolIn: Busca perfiles activos filtrando por roles
 * - findByActivoTrue: Todos los perfiles activos
 * - findByEmail: Busca un perfil por su correo electronico
 *
 * Metodos con consultas personalizadas (SQL nativo o JPQL):
 * - findSubordinadosDosNiveles: Usa una consulta recursiva de PostgreSQL
 *   para obtener en una sola llamada todos los subordinados de un jefe
 *   hasta dos niveles abajo (jefe -> encargados -> operarios)
 * - findByCodigos: Busca perfiles por sus codigos internos
 * - findParaContabilidad: Consulta combinada que obtiene perfiles para
 *   los informes de contabilidad, aceptando varios criterios de busqueda
 */
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

    /*
     * METODOS BASICOS (Spring Data JPA genera el SQL automaticamente)
     */

    // Obtiene todos los perfiles ordenados: primero los activos,
    // luego alfabeticamente por apellidos y nombre
    List<perfil> findAllByOrderByActivoDescApellidosAscNameAsc();

    // Busca todos los perfiles que tienen a un jefe directo concreto
    // Sirve para obtener los subordinados directos de una persona
    List<perfil> findByJefeDirecto_Id(UUID jefeId);

    // Busca perfiles activos que tengan uno de los roles indicados
    // Ejemplo: findByActivoTrueAndRolIn([OPERARIO, ENCARGADO])
    List<perfil> findByActivoTrueAndRolIn(List<user_rol> roles);

    // Obtiene todos los perfiles que estan marcados como activos
    List<perfil> findByActivoTrue();

    // Busca un perfil por su direccion de correo electronico
    // Devuelve Optional porque puede no existir
    Optional<perfil> findByEmail(String username);

    /*
     * METODOS CON CONSULTAS PERSONALIZADAS
     */

    /*
     * findSubordinadosDosNiveles: Obtiene todos los subordinados de un jefe
     * hasta dos niveles jerarquicos.
     *
     * Que hace: Usa una "CTE recursiva" de PostgreSQL, que es una consulta
     * que se llama a si misma para recorrer la jerarquia.
     *
     * Como funciona:
     * 1. Primero encuentra los subordinados directos del jefe
     * 2. Luego, para cada subordinado que sea ENCARGADO, busca SUS subordinados
     * 3. Une todo en un solo resultado
     *
     * Recibe: jefeId - el UUID del jefe
     * Devuelve: lista de perfiles (subordinados directos + subordinados de encargados)
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

    /*
     * findByCodigos: Busca perfiles por sus codigos internos
     *
     * Recibe: una lista de codigos (Strings)
     * Devuelve: los perfiles que tienen esos codigos
     *
     * Importante: no llamar con la lista vacia porque generaria
     * una consulta SQL "IN ()" que es invalida
     */
    @Query("SELECT p FROM perfil p WHERE p.codigo IN :codigos")
    List<perfil> findByCodigos(@Param("codigos") Collection<String> codigos);

    /*
     * findParaContabilidad: Busca perfiles para los informes de contabilidad
     *
     * Es una consulta combinada que cubre tres casos en un solo viaje a BD:
     * 1. Perfiles que aparecen en partes de trabajo (buscados por codigo)
     * 2. Subordinados directos de un jefe (buscados por ID)
     * 3. Todos los OPERARIO/ENCARGADO activos (cuando incluirTodos=true)
     *
     * Recibe:
     * - codigos: lista de codigos de trabajadores
     * - ids: lista de IDs de perfiles
     * - roles: roles a incluir si incluirTodos=true
     * - incluirTodos: si es true, incluye todos los activos con los roles dados
     *
     * Devuelve: lista de perfiles que cumplen alguna de las condiciones
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