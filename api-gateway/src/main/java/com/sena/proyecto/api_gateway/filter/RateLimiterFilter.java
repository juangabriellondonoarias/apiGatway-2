package com.sena.proyecto.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFilter extends AbstractGatewayFilterFactory<RateLimiterFilter.Config> {

    // Mapa thread-safe en memoria para almacenar los baldes de tokens por dirección IP del cliente
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterFilter() {
        super(Config.class);
    }

    public static class Config {
        private int capacity = 10;      // Capacidad máxima de peticiones (tokens)
        private int refillRate = 2;     // Tokens recargados por segundo

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillRate() { return refillRate; }
        public void setRefillRate(int refillRate) { this.refillRate = refillRate; }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String ip = exchange.getRequest().getRemoteAddress() != null 
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                    : "unknown";

            // Obtener o crear el balde de tokens para la IP del cliente
            TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(config.getCapacity(), config.getRefillRate()));

            // Intentar consumir 1 token para la petición actual
            if (bucket.tryConsume()) {
                return chain.filter(exchange);
            } else {
                return onRateLimitExceeded(exchange);
            }
        };
    }

    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String jsonResponse = String.format(
                "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Has superado el límite de peticiones permitido. Por favor, espera un momento.\",\"path\":\"%s\"}",
                Instant.now().toString(),
                exchange.getRequest().getPath().value()
        );

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    // Estructura interna thread-safe que gestiona el algoritmo Token Bucket
    private static class TokenBucket {
        private final long capacity;
        private final long refillRate;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(long capacity, long refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double elapsedTimeInSeconds = (now - lastRefillTimestamp) / 1000.0;
            tokens = Math.min(capacity, tokens + (elapsedTimeInSeconds * refillRate));
            lastRefillTimestamp = now;
        }
    }
}
