import styles from "./HexLogo.module.css";

interface Props {
  size?: number;
  /** prefixo único dos ids de gradiente/filtro — várias instâncias por página */
  id: string;
}

// Nós de uma rede em arranjo hexagonal, ligados a um núcleo central.
const NOS: [number, number][] = [
  [32, 11], [50.19, 21.5], [50.19, 42.5], [32, 53], [13.81, 42.5], [13.81, 21.5],
];
const ANEL = `M32 11 L50.19 21.5 L50.19 42.5 L32 53 L13.81 42.5 L13.81 21.5 Z`;

/**
 * Marca do Infnet Hub: um hub de rede — nós satélites em anel hexagonal,
 * conexões que se desenham, e no centro um aluno modelado em 3D com a mesma
 * iluminação luminosa do núcleo (esfera radial + brilho especular + halo).
 */
export default function HexLogo({ size = 20, id }: Props) {
  const u = (s: string) => `${id}-${s}`;
  return (
    <svg className={styles.hex} width={size} height={size} viewBox="0 0 64 64" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id={u("con")} x1="10" y1="8" x2="54" y2="56" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#5aa8ff" />
          <stop offset="100%" stopColor="#22d3ee" />
        </linearGradient>
        <radialGradient id={u("no")} cx="0.4" cy="0.35" r="0.75">
          <stop offset="0%" stopColor="#eaf6ff" />
          <stop offset="55%" stopColor="#5ab4f5" />
          <stop offset="100%" stopColor="#2a7fd8" />
        </radialGradient>
        {/* aluno 3D luminoso: branco no alto, ciano vivo na base — contrasta
            com as linhas azuis e vira o ponto focal da marca */}
        <radialGradient id={u("nucleo")} cx="0.36" cy="0.26" r="0.9">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="42%" stopColor="#b9f2ff" />
          <stop offset="100%" stopColor="#2fc6ef" />
        </radialGradient>
        <radialGradient id={u("halo")} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0%" stopColor="#6fe4ff" stopOpacity="0.75" />
          <stop offset="100%" stopColor="#22d3ee" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* anel hexagonal entre os nós — mais presente */}
      <path className={styles.anel} d={ANEL} fill="none" stroke={`url(#${u("con")})`}
            strokeWidth="1.5" strokeLinejoin="round" opacity="0.55" pathLength={1} />

      {/* conexões do núcleo até cada nó — reforçadas */}
      <g className={styles.spokes} stroke={`url(#${u("con")})`} strokeWidth="1.8" strokeLinecap="round" opacity="0.85">
        {NOS.map(([x, y], i) => <line key={i} x1="32" y1="32" x2={x} y2={y} pathLength={1} />)}
      </g>

      {/* nós satélites */}
      <g className={styles.nos} fill={`url(#${u("no")})`}>
        {NOS.map(([x, y], i) => <circle key={i} cx={x} cy={y} r="3" />)}
      </g>

      {/* núcleo: halo pulsante + aluno 3D iluminado + brilho especular */}
      <circle className={styles.halo} cx="32" cy="32" r="13" fill={`url(#${u("halo")})`} />
      <g fill={`url(#${u("nucleo")})`}>
        <path d="M24.3 41 C24.3 35.4 27.9 32.4 32 32.4 C36.1 32.4 39.7 35.4 39.7 41 Z" />
        <circle cx="32" cy="27.4" r="4.9" />
      </g>
      <ellipse cx="30.2" cy="25.8" rx="1.7" ry="1.1" fill="#ffffff" opacity="0.95" transform="rotate(-30 30.2 25.8)" />
    </svg>
  );
}
