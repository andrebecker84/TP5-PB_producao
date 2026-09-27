# Imagem da aplicação central do Infnet Hub.
#
# O contexto de build é a RAIZ do repositório, não esta pasta — ver o
# docker-compose.yml. Um módulo Maven não compila sozinho: ele precisa do pom
# pai, que define a versão do Java, o BOM do Spring Cloud e a configuração do
# processador de anotações. Com o contexto apontado para a pasta do módulo, o
# build falharia em "Non-resolvable parent POM".
#
# Três estágios, cada um com um papel:
#   compilacao  Maven + JDK: resolve dependências e empacota o JAR
#   camadas     separa o JAR nas camadas do Spring Boot (ver abaixo)
#   final       só o JRE, o código e um usuário sem privilégio
#
# Os Dockerfiles dos outros serviços Java seguem este mesmo desenho; os
# comentários ficam aqui, uma vez.

# ── 1. Compilação ─────────────────────────────────────────────────────────────
# Java 25 para acompanhar o <java.version> do pom pai.
FROM maven:3.9-eclipse-temurin-25 AS compilacao
WORKDIR /build

# Os poms vêm antes do código-fonte de propósito: dependência muda pouco e
# código muda a toda hora. Separados assim, editar uma classe reaproveita a
# camada de download; num COPY único, cada alteração baixaria tudo de novo.
#
# Todos os poms de módulo precisam estar presentes mesmo que só um seja
# compilado: o pom pai os declara em <modules>, e o Maven monta o reator inteiro
# antes de decidir o que construir.
COPY pom.xml .
COPY contratos/pom.xml contratos/
COPY infnethub-core/pom.xml infnethub-core/
COPY boletim-service/pom.xml boletim-service/
COPY notificacao-service/pom.xml notificacao-service/
COPY api-gateway/pom.xml api-gateway/
COPY eureka-server/pom.xml eureka-server/

# -pl seleciona o módulo, -am ("also make") arrasta junto o que ele depende —
# aqui, o pom pai e o módulo de contratos.
RUN mvn -B -pl infnethub-core -am dependency:go-offline -q

COPY contratos/src contratos/src
COPY infnethub-core/src infnethub-core/src
# Os testes não rodam aqui: rodam no pipeline de integração contínua, antes de
# qualquer imagem ser construída (.github/workflows/ci.yml). Repeti-los dentro
# do build exigiria Docker dentro do Docker para o Testcontainers.
RUN mvn -B -pl infnethub-core -am package -DskipTests -q

# ── 2. Camadas ────────────────────────────────────────────────────────────────
# O JAR executável é um arquivo só, e qualquer alteração de código mudaria a
# camada inteira da imagem — dezenas de MB de dependências reenviados ao
# registro a cada commit. O modo "tools" do Spring Boot o separa em camadas
# ordenadas da que menos muda para a que mais muda: dependências, carregador,
# dependências SNAPSHOT e, por último, o código da aplicação. Num deploy
# típico, só a última camada — algumas centenas de KB — é nova.
FROM eclipse-temurin:25-jre-alpine AS camadas
WORKDIR /extrair
COPY --from=compilacao /build/infnethub-core/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extraido

# ── 3. Imagem final ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

ARG VERSAO=dev
ARG REVISAO=local
LABEL org.opencontainers.image.title="infnethub-core" \
      org.opencontainers.image.description="Infnet Hub — identidade, feed e vagas; produtor de eventos" \
      org.opencontainers.image.source="https://github.com/andrebecker84/TP5-PB_producao" \
      org.opencontainers.image.version="${VERSAO}" \
      org.opencontainers.image.revision="${REVISAO}" \
      org.opencontainers.image.vendor="André Luis Becker — Instituto Infnet"

# Usuário sem privilégio, com o grupo 0 como primário: é a convenção que
# permite rodar sob qualquer UID atribuído pelo orquestrador (OpenShift faz
# isso) e é o grupo com leitura na chave do certificado gerado para o Compose
# (infra/certificados/gerar.sh). Um processo comprometido dentro do contêiner
# não é root — nem dentro dele.
RUN adduser -S -D -H -u 10001 -G root app

WORKDIR /app
COPY --from=camadas /extrair/extraido/dependencies/ ./
COPY --from=camadas /extrair/extraido/spring-boot-loader/ ./
COPY --from=camadas /extrair/extraido/snapshot-dependencies/ ./
COPY --from=camadas /extrair/extraido/application/ ./

USER 10001
# 21081 acompanha o server.port do application.properties — o TP5 usa um bloco
# de portas próprio para não disputar a 8080 com outros projetos, nem as portas
# das etapas anteriores.
EXPOSE 21081
ENTRYPOINT ["java", "-jar", "app.jar"]
