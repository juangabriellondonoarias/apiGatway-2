package com.sena.proyecto.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/cocina")
    public Mono<ResponseEntity<Map<String, Object>>> cocinaFallback() {
        return buildFallbackResponse("El servicio de cocina está tardando demasiado o no responde. Por favor, intenta de nuevo más tarde.");
    }

    @GetMapping("/fallback/usuarios")
    public Mono<ResponseEntity<Map<String, Object>>> usuariosFallback() {
        return buildFallbackResponse("El servicio de gestión de usuarios está temporalmente fuera de servicio. Por favor, intenta de nuevo más tarde.");
    }

    @GetMapping("/fallback/inventario")
    public Mono<ResponseEntity<Map<String, Object>>> inventarioFallback() {
        return buildFallbackResponse("El servicio de inventario no está disponible actualmente o superó el tiempo de espera.");
    }

    @GetMapping("/fallback/general")
    public Mono<ResponseEntity<Map<String, Object>>> generalFallback() {
        return buildFallbackResponse("El servicio solicitado no está disponible en este momento. Por favor, intenta de nuevo más tarde.");
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", message);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
