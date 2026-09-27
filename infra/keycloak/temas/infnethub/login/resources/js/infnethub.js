/*
 * Tela de login do Infnet Hub, dentro do provedor de identidade.
 *
 * O Keycloak desenha a página no servidor, com o template do keycloak.v2, que
 * este tema herda sem modificar. O que a torna a tela de entrada da
 * plataforma é montado aqui, no navegador: o fundo em meio-tom, o aviso de
 * ambiente acadêmico, a marca no canto, a digital holográfica, a abertura com
 * "Conectando ao provedor de identidade…", a nota sobre a senha e o botão
 * do painel de operação. Sem copiar o template, uma atualização do Keycloak
 * não deixa o tema para trás.
 *
 * A senha continua sendo digitada no formulário do próprio Keycloak — nada
 * aqui toca nos campos, só no que está em volta deles.
 */
(function () {
  "use strict";

  var PLATAFORMA = "http://localhost:21000";

  /*
   * O cliente do pedido de login vem no endereço — na primeira carga e também
   * depois de um erro, nas URLs de login-actions. A tela é uma só: o pedido
   * do Grafana (infnethub-grafana) é levado para a entrada da plataforma.
   */
  function cliente() {
    var id = new URLSearchParams(location.search).get("client_id");
    if (!id) {
      var form = document.getElementById("kc-form-login");
      var acao = form && form.getAttribute("action");
      var achado = acao && acao.match(/[?&]client_id=([^&]+)/);
      id = achado ? decodeURIComponent(achado[1]) : "";
    }
    return id;
  }
  var NO_GRAFANA = function () { return cliente() === "infnethub-grafana"; };
  var parado = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  var ICONES = {
    info: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>',
    fechar: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>',
    chave: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z"/><circle cx="16.5" cy="7.5" r=".5" fill="currentColor"/></svg>',
    cadeado: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>',
    escudo: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>',
    alerta: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>',
    voltar: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>',
    grafana: '<svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M23.02 10.59a8.578 8.578 0 0 0-.862-3.034 8.911 8.911 0 0 0-1.789-2.445c.337-1.342-.413-2.505-.413-2.505-1.292-.08-2.113.4-2.416.62-.052-.02-.102-.044-.154-.064-.22-.089-.446-.172-.677-.247-.231-.073-.47-.14-.711-.197a9.867 9.867 0 0 0-.875-.161C14.557.753 12.94 0 12.94 0c-1.804 1.145-2.147 2.744-2.147 2.744l-.018.093c-.098.029-.2.057-.298.088-.138.042-.275.094-.413.143-.138.055-.275.107-.41.166a8.869 8.869 0 0 0-1.557.87l-.063-.029c-2.497-.955-4.716.195-4.716.195-.203 2.658.996 4.33 1.235 4.636a11.608 11.608 0 0 0-.607 2.635C1.636 12.677.953 15.014.953 15.014c1.926 2.214 4.171 2.351 4.171 2.351.003-.002.006-.002.006-.005.285.509.615.994.986 1.446.156.19.32.371.488.548-.704 2.009.099 3.68.099 3.68 2.144.08 3.553-.937 3.849-1.173a9.784 9.784 0 0 0 3.164.501h.08l.055-.003.107-.002.103-.005.003.002c1.01 1.44 2.788 1.646 2.788 1.646 1.264-1.332 1.337-2.653 1.337-2.94v-.058c0-.02-.003-.039-.003-.06.265-.187.52-.387.758-.6a7.875 7.875 0 0 0 1.415-1.7c1.43.083 2.437-.885 2.437-.885-.236-1.49-1.085-2.216-1.264-2.354l-.018-.013-.016-.013a.217.217 0 0 1-.031-.02c.008-.092.016-.18.02-.27.011-.162.016-.323.016-.48v-.253l-.005-.098-.008-.135a1.891 1.891 0 0 0-.01-.13c-.003-.042-.008-.083-.013-.125l-.016-.124-.018-.122a6.215 6.215 0 0 0-2.032-3.73 6.015 6.015 0 0 0-3.222-1.46 6.292 6.292 0 0 0-.85-.048l-.107.002h-.063l-.044.003-.104.008a4.777 4.777 0 0 0-3.335 1.695c-.332.4-.592.84-.768 1.297a4.594 4.594 0 0 0-.312 1.817l.003.091c.005.055.007.11.013.164a3.615 3.615 0 0 0 .698 1.82 3.53 3.53 0 0 0 1.827 1.282c.33.098.66.14.971.137.039 0 .078 0 .114-.002l.063-.003c.02 0 .041-.003.062-.003.034-.002.065-.007.099-.01.007 0 .018-.003.028-.003l.031-.005.06-.008a1.18 1.18 0 0 0 .112-.02c.036-.008.072-.013.109-.024a2.634 2.634 0 0 0 .914-.415c.028-.02.056-.041.085-.065a.248.248 0 0 0 .039-.35.244.244 0 0 0-.309-.06l-.078.042c-.09.044-.184.083-.283.116a2.476 2.476 0 0 1-.475.096c-.028.003-.054.006-.083.006l-.083.002c-.026 0-.054 0-.08-.002l-.102-.006h-.012l-.024.006c-.016-.003-.031-.003-.044-.006-.031-.002-.06-.007-.091-.01a2.59 2.59 0 0 1-.724-.213 2.557 2.557 0 0 1-.667-.438 2.52 2.52 0 0 1-.805-1.475 2.306 2.306 0 0 1-.029-.444l.006-.122v-.023l.002-.031c.003-.021.003-.04.005-.06a3.163 3.163 0 0 1 1.352-2.29 3.12 3.12 0 0 1 .937-.43 2.946 2.946 0 0 1 .776-.101h.06l.07.002.045.003h.026l.07.005a4.041 4.041 0 0 1 1.635.49 3.94 3.94 0 0 1 1.602 1.662 3.77 3.77 0 0 1 .397 1.414l.005.076.003.075c.002.026.002.05.002.075 0 .024.003.052 0 .07v.065l-.002.073-.008.174a6.195 6.195 0 0 1-.08.639 5.1 5.1 0 0 1-.267.927 5.31 5.31 0 0 1-.624 1.13 5.052 5.052 0 0 1-3.237 2.014 4.82 4.82 0 0 1-.649.066l-.039.003h-.287a6.607 6.607 0 0 1-1.716-.265 6.776 6.776 0 0 1-3.4-2.274 6.75 6.75 0 0 1-.746-1.15 6.616 6.616 0 0 1-.714-2.596l-.005-.083-.002-.02v-.056l-.003-.073v-.096l-.003-.104v-.07l.003-.163c.008-.22.026-.45.054-.678a8.707 8.707 0 0 1 .28-1.355c.128-.444.286-.872.473-1.277a7.04 7.04 0 0 1 1.456-2.1 5.925 5.925 0 0 1 .953-.763c.169-.111.343-.213.524-.306.089-.05.182-.091.273-.135.047-.02.093-.042.138-.062a7.177 7.177 0 0 1 .714-.267l.145-.045c.049-.015.098-.026.148-.041.098-.029.197-.052.296-.076.049-.013.1-.02.15-.033l.15-.032.151-.028.076-.013.075-.01.153-.024c.057-.01.114-.013.171-.023l.169-.021c.036-.003.073-.008.106-.01l.073-.008.036-.003.042-.002c.057-.003.114-.008.171-.01l.086-.006h.023l.037-.003.145-.007a7.999 7.999 0 0 1 1.708.125 7.917 7.917 0 0 1 2.048.68 8.253 8.253 0 0 1 1.672 1.09l.09.077.089.078c.06.052.114.107.171.159.057.052.112.106.166.16.052.055.107.107.159.164a8.671 8.671 0 0 1 1.41 1.978c.012.026.028.052.04.078l.04.078.075.156c.023.051.05.1.07.153l.065.15a8.848 8.848 0 0 1 .45 1.34.19.19 0 0 0 .201.142.186.186 0 0 0 .172-.184c.01-.246.002-.532-.024-.856z"/></svg>'
  };

  /* dica dos botões de entrada: um selo com ícone e uma linha de explicação */
  function dica(selo, icone, textoDoSelo, explicacao) {
    return '<span class="ih-dica" aria-hidden="true"><span class="ih-selo ' + selo + '">' + icone +
      "<span>" + textoDoSelo + "</span></span><span class=\"ih-dica-txt\">" + explicacao + "</span></span>";
  }

  function criar(tag, classe, html) {
    var el = document.createElement(tag);
    if (classe) el.className = classe;
    if (html != null) el.innerHTML = html;
    return el;
  }

  function pronto(fn) {
    if (document.readyState !== "loading") fn();
    else document.addEventListener("DOMContentLoaded", fn);
  }

  /* ── Fundo em meio-tom: uma grade de pontos bem sutil sobre o escuro; a
        meia-lua de luz no topo e duas luzes que passeiam acendem os pontos
        por onde passam. O mesmo desenho de components/ui/FundoMeioTom.tsx ── */
  function fundoMeioTom() {
    var canvas = criar("canvas", "ih-fundo");
    canvas.setAttribute("aria-hidden", "true");
    document.body.insertBefore(canvas, document.body.firstChild);
    var ctx = canvas.getContext("2d");
    if (!ctx) return;
    var PASSO = 8, RAIO = 1.15, OPACIDADE = 0.5, BASE = 0.12, L = 0, A = 0, anterior = 0;
    // o mouse não acende nada: ele só puxa, devagar, as formas de luz para perto de si
    var alvoX = -1, alvoY = -1, cursorX = -1, cursorY = -1, cursorForca = 0, cursorAlvo = 0;
    window.addEventListener("pointermove", function (e) { alvoX = e.clientX; alvoY = e.clientY; cursorAlvo = 1;
      if (cursorX < 0) { cursorX = alvoX; cursorY = alvoY; } });
    document.addEventListener("pointerleave", function () { cursorAlvo = 0; });
    document.documentElement.addEventListener("mouseleave", function () { cursorAlvo = 0; });

    function suave(a, b, x) { var t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    function ajustar() {
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      L = canvas.clientWidth; A = canvas.clientHeight;
      canvas.width = Math.round(L * dpr); canvas.height = Math.round(A * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    function desenhar(ms) {
      var t = ms / 1000, diag = Math.hypot(L, A);
      // a meia-lua: o centro fica acima da tela, só a metade de baixo aparece
      var mx = L / 2, my = -A * 0.2, mrx = Math.max(L * 0.62, 560), mry = A * 0.78;
      // as duas luzes que passeiam
      var l1x = L * (0.5 + 0.34 * Math.sin(t * 0.055)), l1y = A * (0.58 + 0.26 * Math.sin(t * 0.035 + 1)), l1r = diag * 0.3;
      var l2x = L * (0.5 + 0.36 * Math.sin(t * 0.04 + 2.4)), l2y = A * (0.5 + 0.3 * Math.sin(t * 0.05 + 4)), l2r = diag * 0.24;
      // o pulso: ondas de luz que nascem no canto inferior esquerdo e se abrem pela
      // tela, uma atrás da outra, crescendo e se apagando devagar
      var px = L / 2, py = A * 0.5, ox = 0, oy = A, pw = diag * 0.14, PERIODO_ONDA = 16;
      var fases = [(t / PERIODO_ONDA) % 1, (t / PERIODO_ONDA + 0.5) % 1];
      ctx.clearRect(0, 0, L, A);
      // um véu de luz contínuo por baixo dos pontos: é ele que faz o claro se
      // desfazer no escuro em degradê; os pontos só acompanham, de leve
      function veu(cx, cy, r0, r1, pico, a) {
        if (a <= 0.002 || r1 <= 0) return;
        var g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r1);
        var meio = Math.min(0.999, Math.max(0.001, pico));
        g.addColorStop(Math.max(0, r0 / r1), "rgba(59,142,245,0)");
        g.addColorStop(meio, "rgba(59,142,245," + a + ")");
        g.addColorStop(1, "rgba(59,142,245,0)");
        ctx.fillStyle = g; ctx.fillRect(0, 0, L, A);
      }
      cursorX += (alvoX - cursorX) * 0.05; cursorY += (alvoY - cursorY) * 0.05;
      cursorForca += (cursorAlvo - cursorForca) * 0.03;
      // as luzes se inclinam na direção do ponteiro, e a onda se curva perto dele
      var puxa = 0.22 * cursorForca;
      l1x += (cursorX - l1x) * puxa; l1y += (cursorY - l1y) * puxa;
      l2x += (cursorX - l2x) * puxa * 0.7; l2y += (cursorY - l2y) * puxa * 0.7;
      veu(l1x, l1y, 0, l1r * 1.3, 0.001, 0.035);
      veu(l2x, l2y, 0, l2r * 1.3, 0.001, 0.03);
      fases.forEach(function (fase) {
        var R = diag * 1.25 * fase, fim = R + pw * 2;
        veu(ox, oy, Math.max(0, R - pw * 2), fim, R / fim, 0.03 * Math.sin(Math.PI * fase));
      });
      ctx.fillStyle = "#3b8ef5";
      for (var y = PASSO / 2; y < A; y += PASSO) {
        for (var x = PASSO / 2; x < L; x += PASSO) {
          var lua = 1 - suave(0, 1, Math.hypot((x - mx) / mrx, (y - my) / mry));
          // queda gaussiana: a luz se desfaz aos poucos, sem borda entre claro e escuro
          var d1 = Math.hypot(x - l1x, y - l1y) / l1r, d2 = Math.hypot(x - l2x, y - l2y) / l2r;
          var luzes = Math.max(Math.exp(-2.2 * d1 * d1), Math.exp(-2.2 * d2 * d2));
          var onda = 0.5 + 0.5 * Math.sin((x * 0.7 + y) * 0.006 - t * 0.22);
          // cada onda cresce de 0 até além da tela; o seno da fase faz ela
          // surgir e sumir aos poucos, sem aparecer do nada
          var dCanto = Math.hypot(x - ox, y - oy), ang = Math.atan2(oy - y, x - ox), portal = 0;
          for (var f = 0; f < 2; f++) {
            // perto do ponteiro a frente da onda se adianta um pouco, como se fosse atraída
            var dPonteiro = Math.hypot(x - cursorX, y - cursorY) / (diag * 0.16);
            var raio = diag * 1.25 * fases[f] * (1 + 0.05 * Math.sin(4 * ang + t * 0.4))
              + diag * 0.06 * cursorForca * Math.exp(-dPonteiro * dPonteiro);
            var dp = (dCanto - raio) / pw;
            // peristalse: faixas lentas correm por dentro da onda, contraindo e soltando
            var contracao = 0.7 + 0.3 * Math.sin(dCanto * 0.014 - t * 0.6);
            portal = Math.max(portal, Math.sin(Math.PI * fases[f]) * contracao * Math.exp(-dp * dp));
          }
          // o centro, onde fica o cartão, recebe pouca luz: o efeito vive em volta
          var dc = Math.hypot(x - px, y - py) / (diag * 0.22), fora = 1 - 0.8 * Math.exp(-dc * dc);
          var i = Math.min(1, BASE + 0.85 * lua * lua + fora * (0.55 * luzes * (0.6 + 0.4 * onda) + 0.28 * portal));
          // o ponto apagado não some de todo: o salto entre escuro e aceso fica suave
          ctx.globalAlpha = OPACIDADE * (0.3 + 0.7 * i);
          ctx.beginPath(); ctx.arc(x, y, RAIO * (0.45 + 0.55 * i), 0, Math.PI * 2); ctx.fill();
        }
      }
      ctx.globalAlpha = 1;
    }
    function laco(ms) { if (ms - anterior > 33) { desenhar(ms); anterior = ms; } requestAnimationFrame(laco); }
    ajustar();
    window.addEventListener("resize", function () { ajustar(); desenhar(performance.now()); });
    if (parado) desenhar(0); else requestAnimationFrame(laco);
  }

  /* ── Feixe de luz com rastro de partículas, como na plataforma ── */
  // Uma volta só: o cometa atravessa, e no fim explode. aoAvancar recebe, a
  // cada quadro, onde está a cabeça (0 a 1); aoTerminar, o instante da explosão.
  function feixe(canvas, aoAvancar, aoTerminar) {
    var ctx = canvas.getContext("2d");
    if (!ctx) return function () {};
    var quente = [165, 243, 252], frio = [129, 140, 248], aura = "129,140,248";
    var L = 0, A = 0, inicio = 0, anterior = -1, vivo = true, parts = [], PERIODO = 3000, E = 0.65, explodiu = 0;

    function ajustar() {
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      L = canvas.clientWidth; A = canvas.clientHeight;
      canvas.width = Math.round(L * dpr); canvas.height = Math.round(A * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    function suave(t) { return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }
    function cometa(x, y, b, v) {
      var comp = (110 + Math.abs(v) * 10) * E;
      [[5 * E, 0.55, quente.join(","), 1.5 * E], [1.6 * E, 0.9, "255,255,255", 0.6]].forEach(function (f) {
        var g = ctx.createLinearGradient(x, 0, x - comp, 0);
        g.addColorStop(0, "rgba(" + f[2] + "," + f[1] * b + ")");
        g.addColorStop(0.35, "rgba(" + f[2] + "," + f[1] * 0.45 * b + ")");
        g.addColorStop(1, "rgba(" + f[2] + ",0)");
        ctx.save(); ctx.filter = "blur(" + f[3] + "px)"; ctx.fillStyle = g; ctx.beginPath();
        ctx.moveTo(x, y - f[0]);
        ctx.quadraticCurveTo(x - comp * 0.35, y - f[0] * 0.35, x - comp, y);
        ctx.quadraticCurveTo(x - comp * 0.35, y + f[0] * 0.35, x, y + f[0]);
        ctx.closePath(); ctx.fill(); ctx.restore();
      });
    }
    function luz(x, y, b) {
      var g = ctx.createRadialGradient(x, y, 0, x, y, 70 * E);
      g.addColorStop(0, "rgba(" + aura + "," + 0.35 * b + ")"); g.addColorStop(1, "rgba(" + aura + ",0)");
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, 70 * E, 0, Math.PI * 2); ctx.fill();
      g = ctx.createRadialGradient(x, y, 0, x, y, 15 * E);
      g.addColorStop(0, "rgba(255,255,255," + b + ")");
      g.addColorStop(0.25, "rgba(" + quente.join(",") + "," + 0.9 * b + ")");
      g.addColorStop(0.6, "rgba(" + quente.join(",") + "," + 0.25 * b + ")");
      g.addColorStop(1, "rgba(" + quente.join(",") + ",0)");
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, 15 * E, 0, Math.PI * 2); ctx.fill();
      ctx.shadowColor = "rgba(" + quente.join(",") + "," + b + ")"; ctx.shadowBlur = 10 * E;
      ctx.fillStyle = "rgba(255,255,255," + b + ")";
      ctx.beginPath(); ctx.arc(x, y, 2.8 * E, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
    }
    function passo(ms) {
      if (!vivo) return;
      if (!inicio) inicio = ms;
      var t = Math.min(1, (ms - inicio) / PERIODO), x = L * (0.06 + 0.88 * suave(t)), y = A / 2;
      var b = Math.min(1, t / 0.12), v = anterior < 0 ? 0 : x - anterior;
      anterior = t < 0.02 ? -1 : x;
      // no fim da volta, a explosão: faíscas para todos os lados e um anel de luz
      if (t >= 1 && !explodiu) {
        explodiu = ms;
        for (var e = 0; e < 90; e++) {
          var ang = Math.random() * Math.PI * 2, vel = 1 + Math.random() * 5.5, dur = 30 + Math.random() * 40;
          parts.push({ x: x, y: y, vx: Math.cos(ang) * vel, vy: Math.sin(ang) * vel * 0.8,
            vida: dur, total: dur, r: 0.6 + Math.random() * 1.3 });
        }
        if (aoTerminar) aoTerminar();
      }
      // poeira cósmica no contorno da parte já cheia da pílula (8 px de altura),
      // cada grão saindo numa direção e velocidade ao acaso
      if (b > 0.05 && !explodiu) for (var d = 0; d < 5; d++) {
        var lado = Math.random() < 0.5 ? -1 : 1, vidaP = 30 + Math.random() * 50;
        var rumo = Math.random() * Math.PI * 2, forca = 0.1 + Math.random() * 0.85;
        parts.push({ x: L * 0.06 + Math.random() * (x - L * 0.06), y: y + lado * (3 + Math.random() * 4),
          vx: Math.cos(rumo) * forca, vy: Math.sin(rumo) * forca * 0.8 + lado * 0.08,
          vida: vidaP, total: vidaP, r: 0.3 + Math.random() * 0.55, a: 0.65 });
      }
      if (b > 0.05 && !explodiu) for (var k = 0; k < 2; k++) {
        var total = 20 + Math.random() * 30;
        parts.push({ x: x - Math.random() * 4, y: y + (Math.random() - 0.5) * 3,
          vx: -(0.6 + Math.random() * 2.2) - Math.max(0, v) * 0.25, vy: (Math.random() - 0.5) * 0.55,
          vida: total, total: total, r: 0.5 + Math.random() * 0.9 });
      }
      ctx.clearRect(0, 0, L, A);
      ctx.globalCompositeOperation = "lighter";
      for (var i = parts.length - 1; i >= 0; i--) {
        var q = parts[i];
        q.x += q.vx; q.y += q.vy; q.vx *= 0.985; q.vy *= 0.99; q.vida -= 1;
        if (q.vida <= 0) { parts.splice(i, 1); continue; }
        var f = q.vida / q.total, c = [0, 1, 2].map(function (n) { return Math.round(frio[n] + (quente[n] - frio[n]) * f); });
        ctx.fillStyle = "rgba(" + c.join(",") + "," + (q.a || 0.45) * f * f * b + ")";
        ctx.beginPath(); ctx.arc(q.x, q.y, q.r * (0.4 + 0.6 * f), 0, Math.PI * 2); ctx.fill();
      }
      if (!explodiu) { cometa(x, y, b, v); luz(x, y, b); }
      else {
        var idade = (ms - explodiu) / 520;
        if (idade < 1) {
          // o clarão some e o anel se abre
          luz(x, y, 1 - idade);
          ctx.strokeStyle = "rgba(" + quente.join(",") + "," + 0.8 * (1 - idade) + ")";
          ctx.lineWidth = 2 * (1 - idade) + 0.5;
          ctx.beginPath(); ctx.arc(x, y, 6 + 70 * suave(idade), 0, Math.PI * 2); ctx.stroke();
        } else if (!parts.length) { ctx.clearRect(0, 0, L, A); return; }
      }
      ctx.globalCompositeOperation = "source-over";
      if (aoAvancar && !explodiu) aoAvancar((x - L * 0.06) / (L * 0.88));
      requestAnimationFrame(passo);
    }
    ajustar();
    if (parado) { luz(L / 2, A / 2, 1); } else requestAnimationFrame(passo);
    return function () { vivo = false; };
  }

  /* ── Aviso de ambiente, no canto superior direito ── */
  function aviso() {
    var el = criar("aside", "ih-aviso",
      '<span class="ih-aviso-ico">' + ICONES.info + '</span>' +
      '<div class="ih-aviso-txt"><strong>Ambiente de demonstração acadêmica</strong><span>Projeto de Bloco · Infnet</span></div>' +
      '<button type="button" class="ih-aviso-fechar" aria-label="Dispensar aviso">' + ICONES.fechar + '</button>');
    el.setAttribute("role", "status");
    document.body.appendChild(el);
    setTimeout(function () { el.classList.add("ih-aberto"); }, 600);
    el.querySelector("button").addEventListener("click", function () {
      el.classList.add("ih-saindo");
      setTimeout(function () { el.remove(); }, 320);
    });
  }

  /* ── A página de usuário e senha: digital, abertura, nota e atalho ── */
  function paginaDeLogin() {
    var cabecalho = document.querySelector(".pf-v5-c-login__main-header");
    var titulo = document.getElementById("kc-page-title");
    var corpo = document.querySelector(".pf-v5-c-login__main-body");
    var formulario = document.getElementById("kc-form-login");
    var botao = document.getElementById("kc-login");
    var main = document.querySelector(".pf-v5-c-login__main");
    if (!cabecalho || !titulo || !corpo || !formulario || !main) return;

    // a digital holográfica e o rótulo da leitura, logo abaixo do título
    var digital = criar("div", "ih-digital",
      '<div class="ih-palco"><img src="' + recurso("img/digital.svg") + '" alt="" width="92" height="92"><span class="ih-scanner"></span></div>');
    digital.setAttribute("aria-hidden", "true");
    var leitura = criar("p", "ih-leitura", "&nbsp;");
    leitura.setAttribute("aria-live", "polite");
    titulo.insertAdjacentElement("afterend", digital);
    digital.insertAdjacentElement("afterend", leitura);

    // a seta do botão, como na plataforma
    if (botao) botao.insertAdjacentHTML("beforeend", ' <span class="ih-seta" aria-hidden="true">→</span>');

    // a nota sobre a senha, fora do cartão, logo abaixo dele
    main.insertAdjacentElement("afterend", criar("p", "ih-senha",
      '<span class="ih-senha-ico">' + ICONES.chave + '</span><span>Você está no provedor de identidade da Infnet. A senha é conferida só aqui e nunca chega à plataforma.</span>'));

    // dois destinos, um login só. "Entrar" (para o feed) é o envio normal; o botão
    // do Grafana envia o mesmo formulário, mas antes marca o destino num
    // cookie de curta duração (cookies são do host: a plataforma, na porta
    // 21000, o lê). Depois do "acesso liberado", a plataforma confere o papel
    // SUPORTE_TI e abre o Grafana, que aproveita a sessão do Keycloak.
    var paraOGrafana = null;
    if (botao) {
      paraOGrafana = criar("button", "ih-grafana-btn", ICONES.grafana + '<span class="ih-grafana-nome">Grafana</span>' +
        dica("ih-selo-restrito", ICONES.cadeado, "Acesso restrito ao suporte de TI", "Entrar no painel de operação (Grafana)."));
      paraOGrafana.type = "button";
      paraOGrafana.setAttribute("aria-label", "Entrar no painel de operação (Grafana). Acesso restrito ao suporte de TI.");
      botao.insertAdjacentHTML("beforeend",
        dica("ih-selo-feed", ICONES.escudo, "Conta institucional", "Entrar no feed com sua conta da Infnet."));
      botao.insertAdjacentElement("afterend", paraOGrafana);
      botao.parentElement.classList.add("ih-acoes-duplas");
      paraOGrafana.addEventListener("click", function () {
        document.cookie = "ih_destino=grafana; path=/; max-age=300; SameSite=Lax";
        if (formulario.requestSubmit) formulario.requestSubmit(); else formulario.submit();
      });
      botao.addEventListener("click", function () {
        document.cookie = "ih_destino=; path=/; max-age=0; SameSite=Lax";
      });
    }

    // ao enviar: o scanner passa pela digital enquanto a senha é conferida
    formulario.addEventListener("submit", function () {
      document.body.classList.add("ih-lendo");
      leitura.textContent = "Lendo digital…";
      if (botao) botao.innerHTML = '<span class="ih-anel" aria-hidden="true"></span>Lendo…';
      if (paraOGrafana) paraOGrafana.disabled = true;
    });

    // a abertura, no centro da página e antes do cartão: o feixe de luz e as
    // mensagens da conexão; então o cartão entra. Depois de um erro (senha
    // errada), a página volta direto ao formulário — a pessoa já passou por ela.
    var comErro = document.querySelector(".pf-v5-c-alert, #input-error, .kc-feedback-text");
    if (comErro || parado) return;
    var MENSAGENS = ["Conectando ao provedor de identidade…", "Estabelecendo conexão segura…", "Preparando a autenticação…"];
    var abertura = criar("div", "ih-abertura",
      '<div class="ih-palco-feixe" aria-hidden="true"><span class="ih-pill"><span class="ih-pill-cheia"></span></span>' +
      '<canvas class="ih-feixe"></canvas></div><p class="ih-conectando">' + ICONES.chave + "<span>" + MENSAGENS[0] + "</span></p>");
    abertura.setAttribute("role", "status");
    document.body.appendChild(abertura);
    document.body.classList.add("ih-conectando-ativo");
    // a pílula enche junto com o cometa; as mensagens mudam conforme ela avança;
    // no fim, o cometa explode e o cartão se abre a partir do clarão
    var pill = abertura.querySelector(".ih-pill"), texto = abertura.querySelector(".ih-conectando span");
    var etapa = 0;
    function trocar(n) {
      etapa = n;
      texto.classList.add("ih-trocando");
      setTimeout(function () { texto.textContent = MENSAGENS[n]; texto.classList.remove("ih-trocando"); }, 180);
    }
    var parar = feixe(abertura.querySelector("canvas"), function (p) {
      pill.style.setProperty("--p", p.toFixed(3));
      var devida = p < 0.34 ? 0 : p < 0.68 ? 1 : 2;
      if (devida > etapa) trocar(devida);
    }, function () {
      pill.style.setProperty("--p", "1");
      setTimeout(function () {
        abertura.classList.add("ih-saindo");
        document.body.classList.remove("ih-conectando-ativo");
        document.body.classList.add("ih-revelado");
        var u = document.getElementById("username"); if (u) u.focus({ preventScroll: true });
        setTimeout(function () { parar(); abertura.remove(); }, 700);
      }, 180);
    });
  }

  /* ── Aviso de cookies (LGPD) ──
   * O mesmo aviso da plataforma (frontend/src/components/ui/AvisoDeCookies.tsx)
   * e o mesmo cookie, ih_consentimento: cookies são do host, não da porta,
   * então a escolha feita aqui vale lá, e vice-versa. Aceitar e rejeitar têm
   * o mesmo peso visual; a escolha pode ser revista pelo botão no canto. */
  var COOKIE = "ih_consentimento", VERSAO = "1";
  var ICONE_COOKIE = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5"/><path d="M8.5 8.5v.01"/><path d="M16 15.5v.01"/><path d="M12 12v.01"/><path d="M11 17v.01"/><path d="M7 14v.01"/></svg>';
  var ICONE_MENOS = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14"/></svg>';
  var ICONE_SETA = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
  var ICONE_LIXO = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>';

  function lerConsentimento() {
    var bruto = document.cookie.split("; ").filter(function (c) { return c.indexOf(COOKIE + "=") === 0; })[0];
    if (!bruto) return null;
    var partes = decodeURIComponent(bruto.split("=")[1]).split("|");
    if (partes[0] !== VERSAO) return null;
    // versão|preferências|data|janelas — o mesmo formato de frontend/src/lib/consentimento.ts
    return { preferencias: partes[1] === "1", janelas: (partes[3] || partes[1]) === "1" };
  }
  function salvarConsentimento(preferencias, janelas) {
    var valor = [VERSAO, preferencias ? "1" : "0", new Date().toISOString(), janelas ? "1" : "0"].join("|");
    document.cookie = COOKIE + "=" + encodeURIComponent(valor) + "; path=/; max-age=31536000; SameSite=Lax";
  }

  function avisoDeCookies() {
    var ESSENCIAIS = [
      ["SESSION", "Mantém você conectado à plataforma. Não é legível por scripts da página (HttpOnly).", "até sair ou fechar o navegador"],
      ["XSRF-TOKEN", "Impede que outro site envie ações em seu nome (proteção contra falsificação de requisições).", "a sessão"],
      ["KEYCLOAK_SESSION, AUTH_SESSION_ID, KC_AUTH_SESSION_HASH, KC_RESTART", "Login no provedor de identidade da Infnet, onde a senha é digitada.", "até sair"],
      [COOKIE, "Guarda esta escolha, para não perguntar de novo.", "1 ano"]
    ];
    var PREFERENCIAS = [
      ["infnet-theme", "Tema claro ou escuro da interface (armazenamento local da plataforma).", "até você apagar"],
      ["infnet_status", "Seu status de presença — disponível, ausente, ocupado (armazenamento local da plataforma).", "até você apagar"]
    ];
    var JANELAS = [
      ["infnet-janelas", "Menu lateral compacto ou expandido; mensagens abertas, recolhidas ou fechadas (armazenamento local da plataforma).", "até você apagar ou redefinir"]
    ];
    function lista(itens) {
      return '<ul class="ih-ck-itens">' + itens.map(function (i) {
        return "<li><code>" + i[0] + "</code><span>" + i[1] + "</span><small>Duração: " + i[2] + "</small></li>";
      }).join("") + "</ul>";
    }
    function categoria(id, titulo, desc, controle, itens) {
      return '<div class="ih-ck-cat" data-id="' + id + '"><div class="ih-ck-cat-topo">' +
        '<button type="button" class="ih-ck-expandir"' + (itens ? ' aria-expanded="false"' : " disabled") + ">" +
        (itens ? '<span class="ih-ck-seta">' + ICONE_SETA + "</span>" : "") +
        "<span><strong>" + titulo + "</strong><small>" + desc + "</small></span></button>" + controle + "</div>" +
        (itens ? '<div class="ih-ck-lista" hidden>' + lista(itens) + "</div>" : "") + "</div>";
    }

    var banner = criar("aside", "ih-ck-banner",
      '<div class="ih-ck-topo"><span class="ih-ck-ico">' + ICONE_COOKIE + "</span><strong>Privacidade e cookies</strong>" + '<button type="button" class="ih-ck-fechar" data-acao="minimizar" aria-label="Minimizar" title="Minimizar">' + ICONE_MENOS + "</button>" + "</div>" +
      "<p>Usamos cookies <b>essenciais</b> para manter sua sessão segura. Com sua permissão, guardamos também <b>preferências</b> de interface neste navegador. Não usamos cookies de publicidade nem de rastreamento.</p>" +
      '<div class="ih-ck-acoes"><button type="button" class="ih-ck-sec" data-acao="personalizar">Personalizar</button>' +
      '<button type="button" class="ih-ck-sec" data-acao="rejeitar">Rejeitar opcionais</button>' +
      '<button type="button" class="ih-ck-pri" data-acao="aceitar">Aceitar todos</button></div>');
    banner.setAttribute("role", "dialog"); banner.setAttribute("aria-label", "Aviso de cookies");

    var fundo = criar("div", "ih-ck-fundo",
      '<div class="ih-ck-painel" role="dialog" aria-labelledby="ih-ck-titulo">' +
      '<header class="ih-ck-painel-topo"><span class="ih-ck-ico">' + ICONE_COOKIE + '</span><h2 id="ih-ck-titulo">Privacidade e cookies</h2>' + '<button type="button" class="ih-ck-fechar" data-acao="minimizar" aria-label="Minimizar" title="Minimizar">' + ICONE_MENOS + "</button>" +
      '<button type="button" class="ih-ck-fechar" data-acao="fechar" aria-label="Fechar">' + ICONES.fechar + "</button></header>" +
      '<p class="ih-ck-intro">Escolha o que este navegador pode guardar. Os essenciais não podem ser desligados: sem eles não há como entrar com segurança. Você pode mudar de ideia quando quiser.</p>' +
      categoria("essenciais", "Essenciais", "Sessão, proteção e login — sempre ativos", '<span class="ih-ck-sempre">Sempre ativos</span>', ESSENCIAIS) +
      categoria("preferencias", "Preferências", "Tema e status de presença neste navegador",
        '<label class="ih-ck-chave"><input type="checkbox" id="ih-ck-pref"><span aria-hidden="true"></span><em class="ih-ck-oculto">Permitir preferências</em></label>', PREFERENCIAS) +
      categoria("janelas", "Estado das janelas", "Menu lateral e mensagens como você deixou",
        '<label class="ih-ck-chave"><input type="checkbox" id="ih-ck-jan"><span aria-hidden="true"></span><em class="ih-ck-oculto">Lembrar o estado das janelas</em></label>', JANELAS) +
      categoria("estatistica", "Estatística e publicidade", "Não utilizamos", '<span class="ih-ck-nenhum">Não usamos</span>') +
      '<div class="ih-ck-dados"><strong>Seus dados neste navegador</strong><div><button type="button" class="ih-ck-perigo" data-acao="apagar">' + ICONE_LIXO + " Apagar dados locais e esta escolha</button></div></div>" +
      '<footer class="ih-ck-acoes"><button type="button" class="ih-ck-sec" data-acao="rejeitar">Rejeitar opcionais</button>' +
      '<button type="button" class="ih-ck-sec" data-acao="salvar">Salvar escolhas</button>' +
      '<button type="button" class="ih-ck-pri" data-acao="aceitar">Aceitar todos</button></footer></div>');

    var flutuante = criar("button", "ih-ck-flutuante", ICONE_COOKIE);
    flutuante.type = "button"; flutuante.title = "Privacidade e cookies";
    flutuante.setAttribute("aria-label", "Privacidade e cookies");

    document.body.appendChild(banner); document.body.appendChild(fundo); document.body.appendChild(flutuante);
    var pref = fundo.querySelector("#ih-ck-pref");
    var jan = fundo.querySelector("#ih-ck-jan");

    function mostrar(estado) {
      banner.hidden = estado !== "banner";
      fundo.hidden = estado !== "painel";
      flutuante.hidden = estado !== "nada";
      if (estado === "painel") {
        var c = lerConsentimento();
        pref.checked = !!(c && c.preferencias); jan.checked = !!(c && c.janelas);
      }
    }
    function decidir(preferencias, janelas) {
      salvarConsentimento(preferencias, janelas === undefined ? preferencias : janelas); mostrar("nada");
    }
    function fecharPainel() { mostrar(lerConsentimento() ? "nada" : "banner"); }

    function acao(e) {
      var alvo = e.target.closest("[data-acao]");
      if (alvo) {
        var a = alvo.getAttribute("data-acao");
        if (a === "aceitar") decidir(true);
        else if (a === "rejeitar") decidir(false);
        else if (a === "salvar") decidir(pref.checked, jan.checked);
        else if (a === "personalizar") mostrar("painel");
        else if (a === "fechar") fecharPainel();
        else if (a === "minimizar") mostrar("nada"); // só o ícone no canto, sem decidir nada
        else if (a === "apagar") {
          document.cookie = COOKIE + "=; path=/; max-age=0; SameSite=Lax";
          try { localStorage.clear(); sessionStorage.clear(); } catch (x) { /* sem armazenamento */ }
          location.reload();
        }
        return;
      }
      var exp = e.target.closest(".ih-ck-expandir");
      if (exp && !exp.disabled) {
        var lst = exp.closest(".ih-ck-cat").querySelector(".ih-ck-lista");
        var abrir = lst.hidden;
        lst.hidden = !abrir; exp.setAttribute("aria-expanded", String(abrir));
      }
    }
    banner.addEventListener("click", acao);
    fundo.addEventListener("click", acao);
    // do ícone: sem escolha feita, volta o aviso; com escolha, abre as preferências
    flutuante.addEventListener("click", function () { mostrar(lerConsentimento() ? "painel" : "banner"); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && !fundo.hidden) fecharPainel();
    });

    // nada à vista de início; o aviso entra um pouco depois, só se ainda não há escolha
    banner.hidden = true; fundo.hidden = true; flutuante.hidden = true;
    setTimeout(function () { mostrar(lerConsentimento() ? "nada" : "banner"); }, 700);
  }

  /* ── Página de erro do provedor: o mesmo cartão, com o que houve e o caminho
        de volta. O botão recomeça pela entrada da plataforma, que abre um
        pedido de login novo (o antigo pode ter expirado ou já ter sido usado). ── */
  function paginaDeErro() {
    var titulo = document.getElementById("kc-page-title");
    var main = document.querySelector(".pf-v5-c-login__main");
    if (!titulo || !main) return;
    document.body.classList.add("ih-erro");
    titulo.textContent = "Não foi possível entrar";
    titulo.insertAdjacentElement("beforebegin", criar("span", "ih-erro-ico", ICONES.alerta));
    var corpo = document.querySelector(".pf-v5-c-login__main-body") || main;
    corpo.insertAdjacentHTML("beforeend",
      '<p class="ih-erro-dica">Isso acontece quando o pedido de login expira ou é reaberto em outra aba. ' +
      "Comece de novo pela entrada da plataforma.</p>" +
      '<a class="ih-erro-voltar" href="' + PLATAFORMA + '/login">' + ICONES.voltar + "<span>Voltar à entrada</span></a>");
  }

  // endereço de um recurso do tema, a partir da folha de estilos já carregada
  function recurso(caminho) {
    var css = document.querySelector('link[href*="/css/infnethub.css"]');
    return css ? css.getAttribute("href").replace(/css\/infnethub\.css.*$/, caminho) : caminho;
  }

  pronto(function () {
    // Uma tela de login só. Quem chega pelo endereço do Grafana (cliente
    // infnethub-grafana) vai para a entrada da plataforma com o destino já
    // marcado: depois do login, o papel SUPORTE_TI é conferido e o Grafana
    // abre, aproveitando a sessão do Keycloak.
    if (NO_GRAFANA()) {
      document.cookie = "ih_destino=grafana; path=/; max-age=300; SameSite=Lax";
      location.replace(PLATAFORMA + "/login");
      return;
    }
    document.body.classList.add("ih");
    fundoMeioTom();
    aviso();
    avisoDeCookies();
    var pagina = document.body.getAttribute("data-page-id");
    if (pagina === "login-login") paginaDeLogin();
    else if (document.getElementById("kc-error-message") || pagina === "login-login-page-expired") paginaDeErro();
  });
})();
