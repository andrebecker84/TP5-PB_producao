import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

/**
 * Configuração do ESLint — formato "flat config".
 *
 * O projeto ficou sem lint desde a migração para o Next 16: o comando
 * `next lint` foi removido, e o script do package.json continuava chamando ele.
 * O `next build` roda a verificação de TypeScript e por isso o problema passava
 * despercebido — tipo errado quebrava o build, mas variável não usada, hook com
 * dependência faltando ou `<img>` no lugar de `<Image>` não eram apontados por
 * ninguém.
 *
 * O `eslint-config-next` 16 já exporta flat config nativo (um array), então não
 * é preciso o `FlatCompat` que a maioria dos tutoriais ainda mostra — ele
 * existe para adaptar configurações no formato antigo, `.eslintrc`.
 *
 * Extensão `.mjs` porque o package.json não declara `"type": "module"`, e o
 * arquivo usa `import`.
 */
const config = [
  {
    // Precisa vir antes das demais entradas e ficar sozinho no objeto: um
    // `ignores` isolado é global, enquanto dentro de outra entrada valeria só
    // para ela.
    ignores: [
      ".next/**",
      ".next_stale*/**",
      "out/**",
      "build/**",
      "node_modules/**",
      "next-env.d.ts",
    ],
  },

  ...nextCoreWebVitals,
  ...nextTypescript,

  {
    rules: {
      // Variável de captura intencionalmente não usada — o caso comum é o
      // `catch (e)` em que só interessa que houve falha, e a desestruturação
      // que descarta um campo. Prefixar com _ passa a ser a forma de dizer
      // "isto é de propósito".
      "@typescript-eslint/no-unused-vars": ["warn", {
        argsIgnorePattern: "^_",
        varsIgnorePattern: "^_",
        caughtErrors: "none",
      }],

      // ── Dívida herdada, deliberadamente rebaixada a aviso ──────────────
      // As duas regras abaixo são NOVAS no conjunto que veio com o Next 16, e
      // apontam padrões espalhados por telas escritas nas etapas anteriores —
      // que funcionam e não fazem parte do escopo desta entrega.
      //
      // Rebaixá-las a aviso é escolha consciente: mantê-las como erro faria o
      // lint falhar em código intocado, e o efeito prático seria alguém
      // desligar a verificação inteira. Como aviso, a lista continua visível a
      // cada execução e serve de backlog.
      //
      // O código novo do TP3 não as viola: as ocorrências restantes estão em
      // arquivos anteriores a esta etapa.
      //
      // set-state-in-effect: chamar setState no corpo de um efeito dispara
      // renderização em cascata. Corrigir exige repensar cada efeito, um a um.
      "react-hooks/set-state-in-effect": "warn",
      // refs: leitura de ref.current durante a renderização, na animação da
      // página 404. Mexer ali arrisca quebrar a cena por ganho estético.
      "react-hooks/refs": "warn",
    },
  },
];

export default config;
