"""Extrai do CHANGELOG.md a seção de uma versão, para as notas da release.

As notas são escritas à mão no CHANGELOG, no formato Keep a Changelog, e a
release no GitHub as repete — em vez de uma lista de mensagens de commit, que
diz o que mudou no código, e não o que mudou para quem usa.

Uso: python3 .github/scripts/notas_da_versao.py 1.0.0
"""
import re
import sys

versao = sys.argv[1]
texto = open("CHANGELOG.md", encoding="utf-8").read()

# "## [1.0.0] — 2026-09-27" até o próximo "## [".
padrao = re.compile(r"^## \[" + re.escape(versao) + r"\].*?$(.*?)(?=^## \[|\Z)", re.S | re.M)
achado = padrao.search(texto)
if not achado:
    sys.exit(f"CHANGELOG.md não tem a seção [{versao}]")
print(achado.group(1).strip())
