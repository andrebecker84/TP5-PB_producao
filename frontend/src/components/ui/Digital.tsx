"use client";

import { useId, type CSSProperties } from "react";
import styles from "./Digital.module.css";

/**
 * Impressão digital holográfica, para a tela de entrada.
 *
 * O desenho é o ícone `finger-print` do Famicons (licença MIT). Os traços do
 * ícone são formas preenchidas, e cada uma é pintada em camadas de luz
 * projetada — corpo translúcido em degradê e reflexo iridescente que
 * corre —, recortadas em faixas horizontais como uma imagem de holograma.
 * Por dentro de cada traço correm, devagar, pulsos de luz.
 *
 * Puramente decorativa: quem autentica é o Keycloak, e não biometria.
 */
const TRACOS = [
  "M7.91003 25.25C7.57277 25.253 7.24071 25.1667 6.94753 25C6.73251 24.8791 6.54375 24.7166 6.3923 24.5219C6.24085 24.3272 6.12975 24.1043 6.0655 23.8661C6.00125 23.628 5.98514 23.3794 6.01811 23.135C6.05109 22.8905 6.13248 22.6551 6.25753 22.4425C8.66753 18.42 16 9.03125 32 9.03125C38.9338 9.03125 45.015 10.8525 50.0663 14.4425C54.2238 17.3888 56.5538 20.7237 57.6638 22.33C57.8046 22.5325 57.9034 22.761 57.9545 23.0023C58.0056 23.2435 58.008 23.4926 57.9614 23.7347C57.9148 23.9769 57.8202 24.2073 57.6832 24.4123C57.5463 24.6174 57.3696 24.793 57.1638 24.9287C56.7431 25.2069 56.2306 25.3102 55.7349 25.2169C55.2393 25.1235 54.7995 24.8408 54.5088 24.4287C52.5 21.54 46.375 12.75 32 12.75C17.9688 12.75 11.625 20.8388 9.55878 24.3075C9.39276 24.5972 9.15241 24.8372 8.86256 25.0029C8.57271 25.1686 8.24387 25.2539 7.91003 25.25Z",
  "M40.061 61.9999C39.9015 62.0018 39.7423 61.9838 39.5873 61.9462C27.981 59.0712 23.6473 47.4687 23.471 46.9862L23.4435 46.8799C23.3485 46.5449 21.0198 38.5887 24.5948 33.9287C26.2335 31.8037 28.726 30.7137 32.0173 30.7137C35.0773 30.7137 37.2848 31.6649 38.8023 33.6337C40.0523 35.2412 40.5523 37.2237 41.036 39.1337C42.0523 43.1112 42.786 45.1999 47.0098 45.4149C48.8648 45.5087 50.0835 44.4237 50.7748 43.4999C52.6435 40.9812 52.9685 36.8749 51.5598 33.2499C49.7498 28.5712 43.326 19.7499 31.9998 19.7499C27.1648 19.7499 22.7223 21.3037 19.1598 24.2237C16.211 26.6424 13.8748 30.0574 12.7498 33.5674C10.6635 40.1024 13.3998 50.3749 13.426 50.4687C13.4899 50.7078 13.5053 50.9572 13.4712 51.2023C13.4372 51.4474 13.3544 51.6832 13.2278 51.8959C13.1011 52.1085 12.9332 52.2936 12.7339 52.4403C12.5346 52.5869 12.3079 52.6922 12.0673 52.7499C11.5811 52.8792 11.0636 52.8129 10.6258 52.5652C10.1879 52.3176 9.86447 51.9082 9.72479 51.4249C9.59979 50.9562 6.67729 39.9999 9.08729 32.4474C11.7123 24.2637 19.8135 16.0137 32.0035 16.0137C37.6373 16.0137 42.9598 17.9287 47.4023 21.5449C50.8423 24.3574 53.6523 28.1349 55.1223 31.9112C56.991 36.7324 56.4898 42.1437 53.8485 45.6787C52.0885 48.0362 49.5823 49.2687 46.806 49.1349C39.5735 48.7724 38.2735 43.7237 37.326 40.0412C36.351 36.2637 35.7273 34.4424 32.0035 34.4424C29.9585 34.4424 28.5223 35.0049 27.6285 36.1699C26.4098 37.7637 26.3148 40.2549 26.4498 42.0624C26.5384 43.3217 26.7519 44.5691 27.0873 45.7862C27.3848 46.5362 31.2585 56.0362 40.536 58.3349C40.7758 58.3917 41.0019 58.4956 41.2012 58.6406C41.4005 58.7855 41.5691 58.9686 41.697 59.1792C41.8249 59.3899 41.9097 59.6238 41.9465 59.8675C41.9832 60.1111 41.9712 60.3597 41.911 60.5987C41.7978 61.0021 41.5557 61.3574 41.2217 61.6104C40.8877 61.8634 40.4801 62.0002 40.061 61.9999Z",
  "M25.1636 61.1424C24.9035 61.1429 24.6459 61.0911 24.4062 60.9899C24.1665 60.8888 23.9497 60.7404 23.7686 60.5536C19.1236 55.6786 16.4961 50.2274 15.5074 43.4111V43.3749C14.9524 38.8624 15.7649 32.4736 19.7461 28.0811C22.6849 24.8399 26.8161 23.1924 32.0036 23.1924C38.1386 23.1924 42.9599 26.0449 45.9661 31.4286C48.1474 35.3399 48.5799 39.2374 48.5911 39.3974C48.614 39.6441 48.588 39.8929 48.5145 40.1295C48.4411 40.3661 48.3218 40.5859 48.1633 40.7764C48.0048 40.9668 47.8103 41.1241 47.5909 41.2392C47.3715 41.3544 47.1316 41.4251 46.8849 41.4474C46.387 41.5014 45.8878 41.3575 45.4952 41.0467C45.1025 40.7359 44.8478 40.2831 44.7861 39.7861C44.4568 37.4476 43.7001 35.1896 42.5536 33.1249C40.2236 29.0136 36.6786 26.9236 31.9899 26.9236C27.9399 26.9236 24.7711 28.1424 22.5911 30.5486C19.4486 34.0174 18.8411 39.3611 19.2724 42.8961C20.1386 48.9236 22.4549 53.7186 26.5449 58.0036C26.7156 58.1812 26.8488 58.3913 26.9367 58.6214C27.0246 58.8515 27.0653 59.097 27.0564 59.3431C27.0476 59.5893 26.9893 59.8312 26.8851 60.0544C26.7809 60.2776 26.6329 60.4775 26.4499 60.6424C26.0974 60.9619 25.6393 61.1399 25.1636 61.1424Z",
  "M46.5625 55.7725C42.5 55.7725 39.0463 54.6475 36.2825 52.4113C30.73 47.9375 30.1075 40.6513 30.08 40.3438C30.0409 39.839 30.2039 39.3394 30.5332 38.9548C30.8624 38.5703 31.3309 38.3322 31.8357 38.2931C32.3404 38.254 32.84 38.417 33.2246 38.7462C33.6091 39.0755 33.8472 39.544 33.8863 40.0488C33.9 40.1563 34.455 46.1163 38.735 49.5488C41.2675 51.5713 44.6525 52.3737 48.8238 51.905C49.3223 51.8445 49.8245 51.9839 50.2206 52.2926C50.6167 52.6013 50.8745 53.0543 50.9375 53.5525C50.9648 53.7986 50.9428 54.0477 50.8729 54.2852C50.8029 54.5227 50.6863 54.7439 50.5299 54.9359C50.3735 55.1279 50.1805 55.2868 49.962 55.4034C49.7436 55.52 49.5041 55.5919 49.2575 55.615C48.363 55.7194 47.4632 55.772 46.5625 55.7725ZM49.7725 6.09875C48.1875 5.0675 42.5675 2 32 2C20.9075 2 15.2738 5.38875 14.0275 6.25C13.9452 6.30053 13.8682 6.35915 13.7975 6.425C13.7901 6.43221 13.7804 6.43663 13.77 6.4375C13.571 6.61126 13.4113 6.82544 13.3016 7.06577C13.1919 7.3061 13.1347 7.56706 13.1338 7.83125C13.1372 8.0789 13.1895 8.32344 13.2876 8.55085C13.3856 8.77827 13.5277 8.9841 13.7054 9.15654C13.8832 9.32898 14.0933 9.46464 14.3236 9.55575C14.5539 9.64686 14.7999 9.69164 15.0475 9.6875C15.4445 9.68723 15.8318 9.56554 16.1575 9.33875C16.2113 9.29875 21.06 5.73625 32.0038 5.73625C42.9475 5.73625 47.8225 9.28625 47.875 9.3125C48.2078 9.55929 48.612 9.69093 49.0263 9.6875C49.2741 9.6913 49.5203 9.64612 49.7506 9.55454C49.981 9.46297 50.1909 9.32681 50.3685 9.15387C50.5461 8.98094 50.6878 8.77464 50.7854 8.54681C50.8831 8.31898 50.9348 8.07411 50.9375 7.82625C50.9376 7.45498 50.8268 7.09216 50.6192 6.78435C50.4116 6.47654 50.1168 6.23779 49.7725 6.09875Z",
];

/** Pseudoaleatório determinístico: o mesmo ritmo no servidor e no navegador. */
function acaso(n: number) {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}

export default function Digital({ estado, semEntrada = false }: {
  estado: "lendo" | "escaneando" | "confirmado";
  /** já estava na tela (continua o cartão do provedor): sem a animação de surgir */
  semEntrada?: boolean;
}) {
  const id = useId().replace(/:/g, "");
  const metal = `metal-${id}`, holo = `holo-${id}`, recorte = `recorte-${id}`;
  const faixas = `faixas-${id}`, holograma = `holograma-${id}`;

  const camada = (classe: string, tinta?: string, pulso = false) => (
    <g className={classe} fill={pulso ? "none" : tinta}>
      {TRACOS.map((d, i) => (
        <path key={i} d={d} pathLength={pulso ? 1 : undefined}
              style={pulso ? {
                // ritmos diferentes por traço: os pulsos não andam juntos
                ["--dur" as string]: `${4.2 + acaso(i + 50) * 2.8}s`,
                ["--atraso" as string]: `${(acaso(i + 90) * 3).toFixed(2)}s`,
              } as CSSProperties : undefined} />
      ))}
    </g>
  );

  return (
    <div className={styles.palco} data-estado={estado} data-sem-entrada={semEntrada || undefined} aria-hidden>
      <div className={styles.emblema}>
        <svg className={styles.desenho} viewBox="0 0 64 64" fill="none">
          <defs>
            <linearGradient id={metal} x1="0" y1="0" x2="0" y2="64" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#a5f3fc" />
              <stop offset=".45" stopColor="#38bdf8" />
              <stop offset="1" stopColor="#6366f1" />
            </linearGradient>
            {/* reflexo iridescente: uma faixa de cores que desliza na diagonal */}
            <linearGradient id={holo} x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
              <stop offset="0"   stopColor="#22d3ee" stopOpacity="0" />
              <stop offset=".3"  stopColor="#22d3ee" stopOpacity=".9" />
              <stop offset=".5"  stopColor="#a78bfa" stopOpacity=".9" />
              <stop offset=".7"  stopColor="#3b82f6" stopOpacity=".9" />
              <stop offset="1"   stopColor="#3b82f6" stopOpacity="0" />
              <animateTransform attributeName="gradientTransform" type="translate"
                                values="-64 -64; 64 64" dur="6s" repeatCount="indefinite" />
            </linearGradient>
            {/* o próprio desenho como recorte: os pulsos só aparecem por dentro dos traços */}
            <clipPath id={recorte}>
              {TRACOS.map((d, i) => <path key={i} d={d} />)}
            </clipPath>
            {/* faixas horizontais de holograma, subindo devagar; ficam no próprio
                desenho, sem caixa em volta que recorte o brilho */}
            <pattern id={faixas} width="4" height="2.4" patternUnits="userSpaceOnUse">
              <rect width="4" height="1.6" fill="#fff" />
              <rect y="1.6" width="4" height=".8" fill="#fff" fillOpacity=".45" />
              <animateTransform attributeName="patternTransform" type="translate"
                                values="0 0; 0 -2.4" dur="1.2s" repeatCount="indefinite" />
            </pattern>
            <mask id={holograma} maskUnits="userSpaceOnUse" x="-24" y="-24" width="112" height="112">
              <rect x="-24" y="-24" width="112" height="112" fill={`url(#${faixas})`} />
            </mask>
          </defs>
          {camada(styles.eco, `url(#${metal})`)}
          <g mask={`url(#${holograma})`}>
            {camada(styles.metal, `url(#${metal})`)}
            {camada(styles.iridescente, `url(#${holo})`)}
            {/* o pulso: três camadas que terminam no mesmo ponto — rastro longo e
                fraco, trecho médio e ponta curta e acesa —, largas o bastante para
                encher a espessura do traço, e recortadas por ele */}
            <g clipPath={`url(#${recorte})`}>
              {camada(`${styles.neon} ${styles.rastro}`, undefined, true)}
              {camada(`${styles.neon} ${styles.corpo}`, undefined, true)}
              {camada(`${styles.neon} ${styles.ponta}`, undefined, true)}
            </g>
          </g>
        </svg>
        <span className={styles.scanner} />
      </div>
    </div>
  );
}
