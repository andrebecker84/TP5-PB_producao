"""Resumo dos testes e da cobertura para a página da execução no GitHub Actions.

Lê os relatórios que o Maven deixa em cada módulo — Surefire (testes) e JaCoCo
(cobertura) — e escreve uma tabela em Markdown na saída padrão; o workflow a
acrescenta a $GITHUB_STEP_SUMMARY. Só a biblioteca padrão: nenhuma ação de
terceiros com acesso ao repositório para mostrar quatro números.

Uso: python3 .github/scripts/resumo_dos_testes.py >> "$GITHUB_STEP_SUMMARY"
"""
import csv
import glob
import os
import xml.etree.ElementTree as ET

MODULOS = ["contratos", "infnethub-core", "boletim-service", "notificacao-service",
           "api-gateway", "eureka-server"]


def testes(modulo):
    total = falhas = erros = ignorados = 0
    tempo = 0.0
    for arquivo in glob.glob(os.path.join(modulo, "target", "surefire-reports", "TEST-*.xml")):
        raiz = ET.parse(arquivo).getroot()
        total += int(raiz.get("tests", 0))
        falhas += int(raiz.get("failures", 0))
        erros += int(raiz.get("errors", 0))
        ignorados += int(raiz.get("skipped", 0))
        tempo += float(raiz.get("time", 0) or 0)
    return total, falhas, erros, ignorados, tempo


def cobertura(modulo):
    """Cobertura de linhas e de desvios (branches), do jacoco.csv do módulo."""
    arquivo = os.path.join(modulo, "target", "site", "jacoco", "jacoco.csv")
    if not os.path.exists(arquivo):
        return None
    linhas = [0, 0]
    desvios = [0, 0]
    with open(arquivo, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            linhas[0] += int(r["LINE_COVERED"])
            linhas[1] += int(r["LINE_COVERED"]) + int(r["LINE_MISSED"])
            desvios[0] += int(r["BRANCH_COVERED"])
            desvios[1] += int(r["BRANCH_COVERED"]) + int(r["BRANCH_MISSED"])
    pct = lambda c, t: f"{100 * c / t:.0f}%" if t else "—"
    return pct(*linhas), pct(*desvios)


print("## Testes do back-end\n")
print("| Módulo | Testes | Falhas | Erros | Ignorados | Tempo | Linhas cobertas | Desvios cobertos |")
print("|---|---:|---:|---:|---:|---:|---:|---:|")
soma = [0, 0, 0, 0, 0.0]
for modulo in MODULOS:
    t = testes(modulo)
    c = cobertura(modulo) or ("—", "—")
    soma = [a + b for a, b in zip(soma, t)]
    situacao = "✅" if t[1] == t[2] == 0 else "❌"
    print(f"| {situacao} `{modulo}` | {t[0]} | {t[1]} | {t[2]} | {t[3]} | {t[4]:.0f} s | {c[0]} | {c[1]} |")
print(f"| **Total** | **{soma[0]}** | **{soma[1]}** | **{soma[2]}** | **{soma[3]}** | {soma[4]:.0f} s | | |")
