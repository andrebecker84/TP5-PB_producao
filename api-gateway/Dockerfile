# Imagem do API Gateway.
# Contexto de build: a RAIZ do repositório. O desenho dos três estágios —
# compilação, camadas e imagem final sem root — está comentado em
# infnethub-core/Dockerfile.

# ── 1. Compilação ─────────────────────────────────────────────────────────────
FROM maven:3-eclipse-temurin-24 AS compilacao
WORKDIR /build

COPY pom.xml .
COPY contratos/pom.xml contratos/
COPY infnethub-core/pom.xml infnethub-core/
COPY boletim-service/pom.xml boletim-service/
COPY notificacao-service/pom.xml notificacao-service/
COPY api-gateway/pom.xml api-gateway/
COPY eureka-server/pom.xml eureka-server/

RUN mvn -B -pl api-gateway -am dependency:go-offline -q

COPY api-gateway/src api-gateway/src
RUN mvn -B -pl api-gateway -am package -DskipTests -q

# ── 2. Camadas ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS camadas
WORKDIR /extrair
COPY --from=compilacao /build/api-gateway/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extraido

# ── 3. Imagem final ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

ARG VERSAO=dev
ARG REVISAO=local
LABEL org.opencontainers.image.title="api-gateway"       org.opencontainers.image.description="Infnet Hub — ponto único de entrada (BFF OAuth2)"       org.opencontainers.image.source="https://github.com/andrebecker84/TP5-PB_producao"       org.opencontainers.image.version="${VERSAO}"       org.opencontainers.image.revision="${REVISAO}"       org.opencontainers.image.vendor="André Luis Becker — Instituto Infnet"

RUN adduser -S -D -H -u 10001 -G root app

WORKDIR /app
COPY --from=camadas /extrair/extraido/dependencies/ ./
COPY --from=camadas /extrair/extraido/spring-boot-loader/ ./
COPY --from=camadas /extrair/extraido/snapshot-dependencies/ ./
COPY --from=camadas /extrair/extraido/application/ ./

USER 10001
# 21080: porta do navegador (HTTP); 21443: porta principal (HTTPS no Compose).
EXPOSE 21080 21443
ENTRYPOINT ["java", "-jar", "app.jar"]
