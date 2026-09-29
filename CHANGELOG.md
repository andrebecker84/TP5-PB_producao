# Changelog

Mudanças do Infnet Hub a cada entrega do Projeto de Bloco. O formato segue o
[Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), e as versões, o
[Versionamento Semântico](https://semver.org/lang/pt-BR/): cada entrega é uma
versão, e a última — a que vai para produção — é a 1.0.0.

As versões 0.1.0 a 0.4.0 vivem nos repositórios de cada etapa; a partir da 1.0.0,
cada versão é uma tag deste repositório, e o pipeline publica a release com o
texto da sua seção (`.github/scripts/notas_da_versao.py`).

## [Não lançado]

### Corrigido

- **CD falhando às vezes, com o mesmo código**: a coleção HTTP conferia a
  chegada dos avisos poucas requisições depois de pedi-los, e num cluster
  recém-criado eles às vezes ainda não tinham chegado. A conferência passa à
  segunda rodada do `testes/e2e.sh` (`13-avisos-desfecho.http`), junto com o
  desfecho da saga, e essa rodada, que só lê, é repetida por até 30 s.
- O CD rodava duas vezes a cada release: também o disparava a CI do PR de
  volta para a `develop`, que sai da `main`. Agora só a CI do push na `main`.

## [1.0.2] — 2026-09-29

### Segurança

- Dependências indiretas do front-end atualizadas para as versões corrigidas
  (`npm audit fix`, só o `package-lock.json`): `brace-expansion`,
  `browserslist`, `js-yaml` e `baseline-browser-mapping`, com falhas de
  negação de serviço por consumo de CPU ou memória. Nenhuma chega ao
  navegador: três são do lint e da compilação, e a quarta vem com o Next.js
  e só lê a tabela de compatibilidade dos navegadores.

## [1.0.1] — 2026-09-28

Correções de estabilidade e desempenho encontradas na operação da 1.0.0.

### Corrigido

- **Notificação de post apagado**: apagar um post deixava no sino as
  notificações de curtida e de comentário, com link para um post que não
  existia mais. O core publica `PostRemovidoV1`, e o serviço de notificação
  apaga as notificações daquele post (`V3`, coluna `post_id`) e guarda uma
  lápide, para que uma curtida atrasada não as recrie. As abas abertas tiram
  as notificações da lista na hora, pela conexão ao vivo.
- Curtir ou comentar um post apagado depois que o feed carregou mostrava
  "Erro ao curtir. Tente novamente."; agora avisa que a publicação foi
  removida e a tira do feed.
- **Login "o pedido expirou"** e sessões perdidas: no cluster, a sessão do
  gateway ficava na memória da réplica, e qualquer reinício dela deslogava
  quem estava conectado e quebrava os logins em andamento. A sessão passa ao
  **Redis** (Spring Session), compartilhada pelas réplicas.
- **Número do sino sumindo sozinho**: fechar o popup dava baixa em todas as
  notificações da lista, lidas ou não. Agora uma notificação só deixa de
  contar quando é lida — ao clicar nela, no botão de lida ou em "Marcar todas
  como lidas", novo no topo do popup.
- A tela de login deixada aberta por mais de 30 minutos mostrava "Sua
  tentativa de login expirou. O processo de login será reiniciado.", que
  parecia defeito. É o prazo do Keycloak para concluir um login; o tema passa
  a dizer isso, e que basta entrar de novo.
- **Reinícios em cascata sob carga**: as sondas usavam o prazo padrão de 1
  segundo, e um pod só ocupado era reiniciado pela liveness. As sondas dos
  serviços Java passam a ter 3 s (readiness) e 5 s (liveness), e o
  autoescalonamento espera 30 s de carga antes de criar réplicas, em vez de
  reagir a picos de segundos.

### Mudado

- **Feed em uma requisição**: cada post chega com quem curtiu e os
  comentários, montados pelo core em três consultas. Antes, cada card buscava
  os seus: um feed de 8 posts fazia 17 requisições ao gateway; um de 30, 61.
- Excluir notificações numa aba também as tira das outras abas abertas.
- Dependabot mensal, com as regras do que não entra por PR automático no
  próprio `dependabot.yml`: versão principal nova do TypeScript, do ESLint e
  dos tipos do Node, e as imagens base do Maven e do Node.

## [1.0.0] — 2026-09-27

Quinta entrega: **implantação e operação em produção**. O sistema passa a rodar
num cluster Kubernetes, a ser observado por logs, métricas e traces ligados
entre si, e a ser construído, testado e implantado por um pipeline.

### Adicionado

- **Kubernetes** (`k8s/`, Kustomize): Deployments com sondas de *startup*,
  *readiness* e *liveness*, saída graciosa e `preStop`; StatefulSets para os
  três PostgreSQL, o cluster de três nós do RabbitMQ (descoberta pelo ordinal
  do pod), Loki e Tempo; HorizontalPodAutoscaler no gateway, no core e no
  serviço de notificação; PodDisruptionBudgets; NetworkPolicies negando toda
  entrada que a arquitetura não usa; contêineres sem root, com sistema de
  arquivos só de leitura e sem *capabilities*.
- Overlays **local** (cluster kind de três nós, `k8s/kind.yaml`) e **produção**
  (imagens do GHCR, segredos fora do repositório, Ingress com TLS, JVM de
  produção); `k8s/implantar.sh` sobe o cluster inteiro com um comando.
- Perfil Spring **`k8s`** em cada serviço: descoberta pelo próprio cluster
  (Service + DNS) no lugar do Eureka, com as rotas `lb://` do gateway intactas;
  *readiness* incluindo o banco; logs em JSON.
- **Rastreamento distribuído** com Micrometer Tracing e OpenTelemetry: o
  `traceparent` atravessa gateway, serviços e RabbitMQ — e também a caixa de
  saída, gravado junto com o evento (`V9`/`V4`, coluna `rastreamento`) e
  retomado pelo relay. Um cadastro aparece como um trace só, do navegador ao
  banco do boletim.
- **Agregação de logs**: logs estruturados (ECS) na saída padrão, recolhidos
  pelo **Grafana Alloy** e guardados no **Loki**, com o `traceId` como metadado
  estruturado. **Tempo** para os traces, com mapa de serviços e métricas
  derivadas dos spans no Prometheus.
- Painéis do Grafana **Infnet Hub — serviços** (latência p50/p95/p99, taxa de
  erro, vazão, CPU, memória, pool de conexões) e **Infnet Hub — logs e
  rastreamento**; exemplares ligando o gráfico de latência ao trace.
- **Regras de alerta** no Prometheus (`infra/prometheus/regras.yml`): serviço
  fora do ar ou sem instâncias, taxa de erro, latência, mensagem morta, caixa de saída parada,
  nó do broker fora.
- **GitHub Actions**: `ci.yml` (testes com Testcontainers e cobertura JaCoCo,
  lint/tipos/build do front-end, validação do Compose, dos manifestos com
  kubeconform, das regras com promtool, do Alloy e dos próprios workflows;
  imagens publicadas no GHCR; release nas tags) e `cd.yml` (cluster kind no
  executor, implantação das imagens do commit, coleção HTTP de ponta a ponta e
  teste de carga).
- **Testes**: teste de carga k6 (`testes/carga/`) com limites de latência e
  erro; `testes/e2e.sh`, a coleção HTTP contra qualquer ambiente; testes do
  rastreamento pela caixa de saída (unidade e integração com RabbitMQ real) e
  do filtro de observações.
- Dependabot, modelos de pull request e de issue, CODEOWNERS.
- **Git Flow**: `main` (produção, tags) e `develop` (integração) protegidas;
  `feature/*`, `fix/*`, `release/*` e `hotfix/*` por pull request; a CI roda em
  todas, e só a `main` e as tags publicam imagens e implantam.

### Mudado

- Dockerfiles em três estágios: compilação, camadas do Spring Boot (só a camada
  da aplicação muda a cada commit) e imagem final **sem root**, com rótulos OCI.
  O front-end roda como o usuário `node`.
- Bloco de portas próprio: **21xxx** (o TP4 usa 20xxx), e `container_name` com
  sufixo `tp5`.
- A carga de demonstração responde aos perfis `dev` **e** `demo`: o Kubernetes
  roda com a configuração de produção (`prod,k8s`) e ainda assim tem os dados
  de exemplo; basta tirar `demo` para a base vazia de uma implantação real.
- `verificar.sh` passa a delegar a coleção HTTP a `testes/e2e.sh`.

### Segurança

- **Autoria pelo token** em posts, comentários e curtidas. Até aqui o cliente
  informava o autor (`autorId` no corpo, `usuarioId` na query string), herança
  do TP4: qualquer conta autenticada podia publicar em nome de outra e editar
  ou apagar o que não era seu. Agora o id sai do claim `usuario_id` do token
  (`Solicitante`), e só o autor ou a moderação (secretaria, coordenação)
  editam e apagam — os demais recebem 403. Um comentário só é alcançado pelo
  post a que pertence. Cobertura: `SolicitanteTest`, `PostServiceAutoriaTest`
  e dois casos 403 na coleção HTTP.
- **Cadastro de pessoas restrito à administração**: criar (`POST`) e alterar
  (`PUT`) usuários estava aberto a qualquer conta autenticada — um aluno
  trocava o nome e o e-mail de outra pessoa, e a troca seguia por evento para
  o boletim. Agora só secretaria e coordenação, como já era a remoção; dois
  casos 403 novos na coleção HTTP.

### Corrigido

- Comentário da coleção HTTP que ainda descrevia o cabeçalho `X-Usuario-Id`
  como forma de identificar o autor — a identidade vem do token desde a 0.4.0.

## [0.4.0] — 2026-09-24

Quarta entrega: **arquitetura orientada a eventos com RabbitMQ**
([TP4-PB_eventos](https://github.com/andrebecker84/TP4-PB_eventos)).

- Integração síncrona (OpenFeign) trocada por eventos; réplica local de alunos
  no boletim.
- `notificacao-service`, alimentado por eventos, com entrega ao vivo por SSE.
- Dez padrões de mensagem, com saga coreografada de expurgo (LGPD) e
  compensação; caixa de saída transacional, confirmação de publicação,
  consumidor idempotente, filas quorum e DLQ.
- Keycloak com OIDC, PKCE e PAR; gateway como BFF.
- Cluster de três nós do RabbitMQ; TLS em todos os trechos; Prometheus e
  Grafana.

## [0.3.0] — 2026-08-20

Terceira entrega: **microsserviços com Spring Cloud**
([TP3-PB_microsservicos](https://github.com/andrebecker84/TP3-PB_microsservicos)).

- `boletim-service` com banco próprio; Eureka, Spring Cloud Gateway,
  OpenFeign e Resilience4j.

## [0.2.0] — 2026-08-19

Segunda entrega: **persistência com PostgreSQL**
([TP2-PB_persistencia](https://github.com/andrebecker84/TP2-PB_persistencia)).

- PostgreSQL com Flyway, auditoria com Hibernate Envers, erros em JSON.

## [0.1.0] — 2026-06-03

Primeira entrega: **monólito em camadas com Spring Boot**
([TP1-PB_monolito](https://github.com/andrebecker84/TP1-PB_monolito)).

[Não lançado]: https://github.com/andrebecker84/TP5-PB_producao/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/andrebecker84/TP5-PB_producao/releases/tag/v1.0.2
[1.0.1]: https://github.com/andrebecker84/TP5-PB_producao/releases/tag/v1.0.1
[1.0.0]: https://github.com/andrebecker84/TP5-PB_producao/releases/tag/v1.0.0
[0.4.0]: https://github.com/andrebecker84/TP4-PB_eventos
[0.3.0]: https://github.com/andrebecker84/TP3-PB_microsservicos
[0.2.0]: https://github.com/andrebecker84/TP2-PB_persistencia
[0.1.0]: https://github.com/andrebecker84/TP1-PB_monolito
