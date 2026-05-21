package com.sena.proyecto.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String ip = request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "desconocida";

        long startTime = System.currentTimeMillis();

        log.info("[Gateway] -> Petición entrante: {} {} | IP: {}", method, path, ip);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            
            String statusCodeString = response.getStatusCode() != null 
                    ? response.getStatusCode().toString() 
                    : "UNKNOWN";

            log.info("[Gateway] <- Petición completada: {} {} | STATUS: {} | Tiempo: {}ms", 
                    method, path, statusCodeString, duration);
        }));
    }

    @Override
    public int getOrder() {
        // Ejecutamos este filtro al principio de todo para capturar el tiempo de respuesta total
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
