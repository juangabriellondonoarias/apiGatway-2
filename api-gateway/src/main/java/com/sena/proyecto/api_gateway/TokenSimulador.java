package com.sena.proyecto.api_gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

public class TokenSimulador {
    public static void main(String[] args) {
        // Usa la MISMA clave por defecto configurada en application.yaml
        String SECRET_KEY = "TuClaveSecretaSuperSeguraParaElSena2026";
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .setSubject("AprendizSena")
                .claim("rol", "ROLE_ADMIN") // Añadimos el claim "rol" para que sea procesado por el Gateway
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // Expira en 1 hora
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        System.out.println("Copia este token para probar en Postman:");
        System.out.println("Bearer " + token);
    }
}