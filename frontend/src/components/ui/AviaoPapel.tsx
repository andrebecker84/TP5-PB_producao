/* traçado do ícone paper-plane da família Gravity UI (gravity-ui/icons, MIT):
   um path só, com fill-rule evenodd — o contorno já vem embutido no desenho */
export const PAPER_PLANE =
  "M7.29 13.904 5.25 10.75 2.096 8.71a2.4 2.4 0 0 1 .5-4.278l9.273-3.296a2.346 " +
  "2.346 0 0 1 2.996 2.995L13.45 3.63a.844.844 0 0 0-1.08-1.08L3.1 5.846a.9.9 0 " +
  "0 0-.19 1.604l2.78 1.799 3.279-3.28a.75.75 0 1 1 1.06 1.061L6.75 10.31l1.799 " +
  "2.779a.9.9 0 0 0 1.604-.188l3.297-9.272 1.413.502-3.296 9.273a2.4 2.4 0 0 1-4.277.5";

/* `horizontal` gira o desenho para o nariz apontar no sentido do voo — a rota
   usa offset-rotate: auto, que alinha o eixo X do elemento à tangente.
   Girado a 45°, as pontas passam dos 16×16 originais: a caixa é ampliada em 3
   unidades de cada lado (e o elemento cresce na mesma proporção) para o nariz
   e as asas não saírem cortados. */
const CAIXA = 22;

export default function AviaoPapel({ className, size = 34, horizontal = false, style }: {
  className?: string; size?: number; horizontal?: boolean; style?: React.CSSProperties;
}) {
  const lado = horizontal ? (size * CAIXA) / 16 : size;
  return (
    <svg className={className} style={style}
      viewBox={horizontal ? `-3 -3 ${CAIXA} ${CAIXA}` : "0 0 16 16"}
      width={lado} height={lado} fill="none" overflow="visible">
      <g transform={horizontal ? "rotate(45 8 8)" : undefined}>
        <path fill="currentColor" fillRule="evenodd" clipRule="evenodd" d={PAPER_PLANE} />
      </g>
    </svg>
  );
}
