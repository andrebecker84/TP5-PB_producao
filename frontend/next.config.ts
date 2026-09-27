import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  output: "standalone",

  // fixa a raiz do workspace no diretório do front-end: sem isto o Turbopack
  // infere C:\Users\becke (por um package-lock.json perdido lá) e chega a
  // resolver o mesmo módulo por dois caminhos via OneDrive.
  turbopack: {
    root: path.resolve(__dirname),
  },
  devIndicators: {
    position: "top-right",
  },
  // Proxy opcional: /api/* → API. O cliente hoje chama a API por URL absoluta
  // (NEXT_PUBLIC_API_URL), então isto só entra em ação se alguém usar caminho
  // relativo. Usa variável própria (API_PROXY_TARGET) e NÃO a
  // NEXT_PUBLIC_API_URL: esta já termina em /api/v1 e duplicaria o prefixo
  // (/api/v1/v1/...).
  //
  // O rewrite roda no SERVIDOR: dentro do contêiner, "localhost" é o próprio
  // front-end, não a API — por isso o compose passa API_PROXY_TARGET com o nome
  // do serviço na rede do Docker. Sem essa variável o caminho respondia 500 com
  // ECONNREFUSED, em vez do conteúdo da API.
  //
  // O destino é o GATEWAY, e não o monolito como no TP2, quando havia um
  // back-end só: agora as rotas de dois serviços chegam pelo mesmo lugar, e
  // apontar direto para um deles deixaria o outro fora do proxy.
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.API_PROXY_TARGET || "http://localhost:21080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
