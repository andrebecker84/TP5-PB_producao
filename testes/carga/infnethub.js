// =========================================================================
// Teste de carga do Infnet Hub (k6).
//
// Simula pessoas usando a plataforma pelo gateway: abrem o feed, as vagas, o
// boletim e o sino de notificações, e de vez em quando curtem um post — o que
// gera um evento, que atravessa o RabbitMQ e vira notificação no outro serviço.
// A carga exercita, portanto, os dois caminhos: o síncrono (HTTP) e o
// assíncrono (mensageria).
//
// Dois perfis:
//   fumaca  30 s, 5 usuários — no pipeline de CD, depois de cada implantação:
//           prova que o sistema aguenta tráfego, e não só uma requisição
//   escala  7 min, até 60 usuários — na demonstração: é a carga que faz o
//           autoescalonamento do Kubernetes criar réplicas (kubectl get hpa -w)
//
// Os limites (thresholds) fazem o k6 terminar com erro se forem violados: é
// o que transforma "rodou" em "passou".
//
//   docker run --rm -i -e PERFIL=escala -e BASE_URL=http://host.docker.internal:21080 \
//     -e KEYCLOAK_URL=http://host.docker.internal:21180 grafana/k6:2.3.0 run - < testes/carga/infnethub.js
//
// (ou ./testes/carga/rodar.sh escala)
// =========================================================================
import http from "k6/http";
import { check, group, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:21080";
const KEYCLOAK = __ENV.KEYCLOAK_URL || "http://localhost:21180";
const PERFIL = __ENV.PERFIL || "fumaca";

const PERFIS = {
  fumaca: { vus: 5, duration: "30s" },
  escala: {
    stages: [
      { duration: "1m", target: 20 },
      { duration: "2m", target: 60 },
      { duration: "3m", target: 60 },
      { duration: "1m", target: 0 },
    ],
  },
};

export const options = {
  ...PERFIS[PERFIL],
  thresholds: {
    // Menos de 1% de falhas, e o p95 abaixo de 800 ms — os mesmos limiares
    // que as regras de alerta (infra/prometheus/regras.yml) usam para acordar
    // alguém, com folga.
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<800", "p(99)<2000"],
    checks: ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "p(50)", "p(95)", "p(99)", "max"],
};

// Cada usuário virtual obtém o próprio token, e o renova antes de expirar
// (o realm emite tokens de 5 minutos, e o perfil de escala dura 7).
let token = null;
let expiraEm = 0;

function autenticar() {
  const r = http.post(
    `${KEYCLOAK}/realms/infnethub/protocol/openid-connect/token`,
    { client_id: "infnethub-dev-cli", grant_type: "password", username: "lucas.mendonca", password: "infnet" },
    { tags: { name: "keycloak token" } },
  );
  check(r, { "token emitido": (x) => x.status === 200 });
  token = r.json("access_token");
  expiraEm = Date.now() + (r.json("expires_in") - 30) * 1000;
}

export default function () {
  if (!token || Date.now() > expiraEm) {
    autenticar();
  }
  const cabecalhos = { headers: { Authorization: `Bearer ${token}` } };

  group("feed", () => {
    const r = http.get(`${BASE}/api/v1/posts`, { ...cabecalhos, tags: { name: "GET /posts" } });
    check(r, { "feed 200": (x) => x.status === 200 });
  });

  group("vagas", () => {
    const r = http.get(`${BASE}/api/v1/vagas`, { ...cabecalhos, tags: { name: "GET /vagas" } });
    check(r, { "vagas 200": (x) => x.status === 200 });
  });

  group("sino", () => {
    const r = http.get(`${BASE}/api/v1/notificacoes/nao-lidas`, { ...cabecalhos, tags: { name: "GET /notificacoes/nao-lidas" } });
    check(r, { "sino 200": (x) => x.status === 200 });
  });

  group("boletim", () => {
    const r = http.get(`${BASE}/api/v1/boletim/1`, { ...cabecalhos, tags: { name: "GET /boletim/{id}" } });
    check(r, { "boletim 200": (x) => x.status === 200 });
  });

  // Uma curtida a cada quatro iterações, num dos posts da carga inicial: vira
  // PostCurtido no core, sai pela caixa de saída e chega como notificação ao
  // autor do post — o caminho assíncrono sob carga.
  //
  // Todos os usuários virtuais são a mesma pessoa, e às vezes dois curtem o
  // mesmo post no mesmo instante. A restrição única do banco
  // (uk_curtidas_post_usuario) aceita um e recusa o outro com 409 — é a regra
  // funcionando sob concorrência, e não uma falha. Por isso o 409 é esperado
  // aqui, e só aqui.
  if (__ITER % 4 === 0) {
    group("curtida", () => {
      const post = 1 + Math.floor(Math.random() * 3);
      const r = http.post(`${BASE}/api/v1/posts/${post}/curtidas`, null, {
        ...cabecalhos,
        tags: { name: "POST /posts/{id}/curtidas" },
        responseCallback: http.expectedStatuses(200, 409),
      });
      check(r, { "curtida aceita ou recusada pela restrição": (x) => x.status === 200 || x.status === 409 });
    });
  }

  sleep(0.5 + Math.random());
}
