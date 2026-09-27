## O que muda

<!-- Uma ou duas frases: o problema e a solução, para quem não acompanhou. -->

## Tipo

- [ ] Correção de defeito
- [ ] Funcionalidade nova
- [ ] Infraestrutura / implantação (Docker, Kubernetes, pipeline)
- [ ] Observabilidade (logs, métricas, traces, painéis)
- [ ] Documentação

## Como foi verificado

<!-- O que foi rodado, e o que se viu. "Os testes passam" não basta: quais, e
     onde — ./mvnw verify, ./testes/e2e.sh, o cenário de demonstração, o painel. -->

## Conferência

- [ ] O pipeline de CI passou (testes, front-end, configuração, imagens)
- [ ] Mudou contrato de mensagem? `doc/asyncapi.yaml` e o módulo `contratos` foram atualizados juntos
- [ ] Mudou schema? Há migration nova do Flyway, e nenhuma antiga foi editada
- [ ] Mudou implantação? Compose **e** Kubernetes (`k8s/`) acompanham
- [ ] `CHANGELOG.md` atualizado na seção `[Não lançado]`
- [ ] Nenhum segredo, `.env` ou dado pessoal real no diff
