# 🛡️ Documentación Oficial: API Gateway & Consul

Este documento resume la arquitectura, funcionalidades principales y responsabilidades del **API Gateway** centralizado del proyecto Gastro SENA, así como la guía estandarizada para conectar cualquier microservicio al ecosistema.

---

## 1. 🚀 ¿Qué hace nuestro API Gateway?

El API Gateway actúa como la **única puerta de entrada** para todas las peticiones que vienen del cliente (Frontend en Angular, móviles, etc.). Ningún cliente se comunica directamente con los microservicios; todo el tráfico pasa y se filtra obligatoriamente por aquí.

Sus principales características son:

### A. Enrutamiento Inteligente y Balanceo de Carga
El Gateway enruta el tráfico hacia tus 7 microservicios (`servicio-usuarios`, `gs-ms-cocina`, `servicio-inventario`, `servicio-bar`, `servicio-inicio-general`, `servicio-reportes`, `servicio-restaurante`). 
* Utiliza el esquema interno `lb://nombre-servicio` para buscar en tiempo real en **Consul** la IP y el puerto de cada microservicio, balanceando la carga automáticamente.

### B. Seguridad Centralizada (El Guardia del Castillo)
Centraliza la validación de seguridad (JWT) para evitar que cada microservicio tenga que programar lógicas de autenticación por separado.
* **Rutas Privadas (`/api/<servicio>/**`)**: Exigen un token JWT válido. El Gateway extrae el token, verifica la firma y, si es correcto, inyecta la identidad del usuario (ej. nombre y rol) en las cabeceras internas (`X-User-Username` y `X-User-Role`) antes de reenviar la petición al microservicio.
* **Rutas Públicas (`/api/<servicio>/public/**`)**: Permiten el acceso libre (sin token). Gracias a una regla inteligente (`StripPrefix=3` y `Method=GET`), el Gateway transforma la URL pública en una URL estándar y solo permite operaciones de lectura (`GET`), garantizando máxima seguridad sin tocar el código Java de los microservicios.

### C. Resiliencia y Prevención de Caídas (Circuit Breaker)
Implementado con **Resilience4j**. Si el microservicio de Cocina se apaga, se satura o sufre un error de base de datos, el Gateway "abre el circuito". Esto significa que corta el paso y responde rápidamente con un JSON de error amigable (`503 Service Unavailable`), evitando que el servidor del Gateway se quede esperando indefinidamente y colapse toda la plataforma.

### D. Limitador de Tráfico (Rate Limiting)
Para prevenir ataques de denegación de servicio (DDoS) o fuerza bruta, incorpora un filtro que limita el número de peticiones por segundo que una misma IP puede hacer hacia los endpoints críticos.

### E. Documentación Unificada (Swagger)
Ofrece un portal centralizado en `http://localhost:8085/swagger-ui.html`. Desde un solo menú desplegable, el equipo de desarrollo puede navegar, probar e inspeccionar la documentación de todas las APIs conectadas, sin tener que ir a los puertos individuales de cada microservicio.

---

## 2. 🔌 Guía de Conexión para Nuevos Microservicios

Para que cualquier microservicio (ej. Inventario, Bar, Usuarios) sea descubierto por Consul y protegido adecuadamente por el API Gateway, debe seguir estos 4 pasos al pie de la letra:

### Paso 1: Dependencias en el `pom.xml`
Todo microservicio debe incluir la dependencia de descubrimiento de Spring Cloud y Actuator (para informar sobre su estado de salud):
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-consul-discovery</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Paso 2: Archivo `application.yaml` (Configuración local)
El microservicio debe declarar un nombre exacto y habilitar los endpoints de monitoreo de salud que Consul requiere:
```yaml
spring:
  application:
    name: gs-ms-cocina # 🚨 DEBE coincidir exactamente con el nombre de ruta 'lb://...' esperado por el Gateway
  
management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

### Paso 3: Configuración en Docker (`docker-compose.yml`)
Dado que el Gateway y los microservicios están dockerizados en redes aisladas, los microservicios deben reportar a Consul su IP utilizando la interfaz virtual de red de Docker (`host.docker.internal`):
```yaml
environment:
  # El host local de Consul, accesible a través de la red del anfitrión (tu Windows)
  SPRING_CONSUL_HOST: host.docker.internal 
  
  # Forzar la activación del descubrimiento
  SPRING_CLOUD_CONSUL_ENABLED: true
  SPRING_CLOUD_CONSUL_DISCOVERY_ENABLED: true
  SPRING_CLOUD_CONSUL_DISCOVERY_PREFER_IP_ADDRESS: true
  
  # 🚨 CRÍTICO: Indica a Consul que la IP donde los demás (Gateway) pueden encontrar a este servicio es la máquina host
  SPRING_CLOUD_CONSUL_DISCOVERY_IP_ADDRESS: host.docker.internal
  PORT: 8082 # El puerto expuesto del microservicio
```

### Paso 4: Consumir la Identidad del Usuario en los Controladores Java
Como el API Gateway ya hizo el trabajo pesado de desencriptar y validar el token JWT, **los microservicios ya no necesitan validar tokens**. Lo único que tienen que hacer es leer las cabeceras inyectadas por el Gateway para saber **quién** hizo la petición.

Esto se configura **directamente en las funciones de tus Controladores (`@RestController`)** que manejan rutas privadas (como crear, editar o borrar), agregando el parámetro `@RequestHeader`.

**Ejemplo Práctico en `RecetaController.java`:**
```java
@PostMapping // Ruta privada protegida por el Gateway
public ResponseEntity<RecetaResponseDTO> crearReceta(
        @Valid @RequestBody RecetaRequestDTO dto,
        
        // 👇 AQUÍ LEES LOS HEADERS INYECTADOS POR EL GATEWAY 👇
        @RequestHeader(value = "X-User-Username", required = false) String username,
        @RequestHeader(value = "X-User-Role", required = false) String role
) {
    
    // Si la petición llega aquí, el Gateway garantiza que es válida. 
    // Ahora puedes usar la variable 'username' o 'role' en tu lógica de negocio.
    System.out.println("El usuario que está creando la receta es: " + username);
    
    // Ejemplo de validación de permisos local:
    if (!"ADMIN".equals(role)) {
        return new ResponseEntity<>(HttpStatus.FORBIDDEN); // Prohibido
    }

    // Continuamos con el flujo normal llamando al servicio...
    RecetaResponseDTO nuevaReceta = recetaService.crearReceta(dto);
    return new ResponseEntity<>(nuevaReceta, HttpStatus.CREATED);
}
```
*(Nota: En rutas públicas, como listar todas las recetas, no necesitas agregar estos `@RequestHeader` ya que el usuario podría ser anónimo).*
