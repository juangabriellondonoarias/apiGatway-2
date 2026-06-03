# ==========================================
# ETAPA 1: Construcción (Compilar el código)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos el pom.xml y descargamos dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiamos el código fuente y compilamos
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# ETAPA 2: Ejecución
# ==========================================
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto de la API Gateway
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
