# Imagem do microsserviço de Desempenho Acadêmico.
# Contexto de build: a RAIZ do repositório. O desenho dos três estágios —
# compilação, camadas e imagem final sem root — está comentado em
# infnethub-core/Dockerfile.

# ── 1. Compilação ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS compilacao
WORKDIR /build

COPY pom.xml .
COPY contratos/pom.xml contratos/
COPY infnethub-core/pom.xml infnethub-core/
COPY boletim-service/pom.xml boletim-service/
COPY notificacao-service/pom.xml notificacao-service/
COPY api-gateway/pom.xml api-gateway/
COPY eureka-server/pom.xml eureka-server/

RUN mvn -B -pl boletim-service -am dependency:go-offline -q

COPY contratos/src contratos/src
COPY boletim-service/src boletim-service/src
RUN mvn -B -pl boletim-service -am package -DskipTests -q

# ── 2. Camadas ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS camadas
WORKDIR /extrair
COPY --from=compilacao /build/boletim-service/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extraido

# ── 3. Imagem final ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

ARG VERSAO=dev
ARG REVISAO=local
LABEL org.opencontainers.image.title="boletim-service"       org.opencontainers.image.description="Infnet Hub — desempenho acadêmico; réplica de alunos por eventos"       org.opencontainers.image.source="https://github.com/andrebecker84/TP5-PB_producao"       org.opencontainers.image.version="${VERSAO}"       org.opencontainers.image.revision="${REVISAO}"       org.opencontainers.image.vendor="André Luis Becker — Instituto Infnet"

RUN adduser -S -D -H -u 10001 -G root app

WORKDIR /app
COPY --from=camadas /extrair/extraido/dependencies/ ./
COPY --from=camadas /extrair/extraido/spring-boot-loader/ ./
COPY --from=camadas /extrair/extraido/snapshot-dependencies/ ./
COPY --from=camadas /extrair/extraido/application/ ./

USER 10001
EXPOSE 21082
ENTRYPOINT ["java", "-jar", "app.jar"]
