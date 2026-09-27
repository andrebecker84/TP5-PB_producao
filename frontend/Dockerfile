# Imagem do front-end (Next.js). Dois estágios: build com as dependências de
# desenvolvimento; execução só com a saída standalone, sem root.

# Build stage
FROM node:24-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# NEXT_PUBLIC_* é inlinado no bundle do cliente EM TEMPO DE BUILD — passar só
# como environment no compose (runtime) não chega ao navegador. Vem como
# build-arg do docker-compose.yml; sem valor, vale o default do código.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
# Também de build: a função rewrites() do next.config.ts é avaliada durante o
# `next build` e o destino fica gravado no routes-manifest. Definir a variável
# apenas no runtime não muda o destino já resolvido — o efeito é o proxy
# insistir no endereço padrão e responder 500 com ECONNREFUSED.
ARG API_PROXY_TARGET
ENV API_PROXY_TARGET=${API_PROXY_TARGET}
RUN npm run build

# Runtime stage
FROM node:24-alpine

ARG VERSAO=dev
ARG REVISAO=local
LABEL org.opencontainers.image.title="frontend"       org.opencontainers.image.description="Infnet Hub — interface web (Next.js)"       org.opencontainers.image.source="https://github.com/andrebecker84/TP5-PB_producao"       org.opencontainers.image.version="${VERSAO}"       org.opencontainers.image.revision="${REVISAO}"       org.opencontainers.image.vendor="André Luis Becker — Instituto Infnet"

WORKDIR /app
ENV NODE_ENV=production

# Saída standalone (next.config.ts: output "standalone"): server.js próprio +
# só as dependências realmente usadas — imagem final sem node_modules completo.
# Os arquivos pertencem ao usuário "node", que a imagem oficial já traz: o
# servidor roda sem root, e o cache de imagens do Next (.next/cache) continua
# gravável por ele.
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public
USER node

# O server.js standalone escuta em localhost por padrão — invisível de fora do
# contêiner. HOSTNAME=0.0.0.0 abre para o mapeamento de portas do compose.
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

EXPOSE 3000
CMD ["node", "server.js"]
