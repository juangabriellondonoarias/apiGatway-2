package com.sena.proyecto.api_gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Order(-1) // Establece una prioridad más alta para que reemplace al DefaultErrorWebExceptionHandler de Spring Boot
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Ha ocurrido un error interno e inesperado en el API Gateway";

        // 1. Manejar microservicio caído o inaccesible (Error de Conexión)
        if (ex instanceof ConnectException || 
            (ex.getMessage() != null && (ex.getMessage().contains("Connection refused") || ex.getMessage().contains("connection refused")))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "El microservicio al que intentas acceder está apagado o fuera de servicio temporalmente.";
            log.error("[Gateway Error] Error de conexión: {}", ex.getMessage());
        } 
        // 2. Manejar excepciones con códigos de estado específicos de Spring (ej. 404 Not Found)
        else if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            HttpStatusCode code = rse.getStatusCode();
            status = HttpStatus.valueOf(code.value());
            message = rse.getReason() != null ? rse.getReason() : "Recurso no encontrado o error en petición";
            log.error("[Gateway Error] ResponseStatusException ({}): {}", code.value(), message);
        } 
        // 3. Cualquier otra excepción inesperada
        else {
            message = ex.getMessage() != null ? ex.getMessage() : "Error sin mensaje específico";
            log.error("[Gateway Error] Excepción no controlada: ", ex);
        }

        response.setStatusCode(status);

        String jsonResponse = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()
        );

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
