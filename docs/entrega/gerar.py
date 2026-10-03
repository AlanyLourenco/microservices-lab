"""Gera o documento de entrega (HTML + PDF) a partir de documento.md.

Diretivas aceitas no Markdown (cada uma sozinha na linha, exceto PENDENTE):
  [[IMG: caminho | legenda]]          figura (SVG/PNG); se o arquivo não existir, vira caixa PENDENTE
  [[ARQUIVO: caminho]]                conteúdo integral de um arquivo, como bloco de código
  [[CODIGO: pasta-do-servico]]        todos os arquivos-fonte de um serviço
  [[EVID: arquivo | inicio | fim]]    trecho de evidencias/<arquivo> entre as linhas que contêm
                                      'inicio' e 'fim' (opcionais); se não existir, PENDENTE
  [[PENDENTE: texto]]                 marcação inline de dado que depende da execução real
Caminhos são relativos à pasta microservices-lab.

Uso: python docs/entrega/gerar.py
"""
import html
import re
import subprocess
import sys
from pathlib import Path

import markdown

RAIZ = Path(__file__).resolve().parents[2]
ENTREGA = Path(__file__).resolve().parent
SAIDA_HTML = ENTREGA / "Laboratorio-Microsservicos-Alany-Gabriely.html"
SAIDA_PDF = ENTREGA / "Laboratorio-Microsservicos-Alany-Gabriely.pdf"
CHROME = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]
LINGUAGENS = {".java": "java", ".yml": "yaml", ".yaml": "yaml", ".xml": "xml", ".sql": "sql", ".sh": "bash"}

pendencias = []


def pendente(texto):
    pendencias.append(texto)
    return f'<span class="pendente">PENDENTE: {html.escape(texto)}</span>'


def bloco_codigo(texto, titulo=None, linguagem=""):
    cab = f'<div class="arquivo">{html.escape(titulo)}</div>' if titulo else ""
    return f'{cab}<pre class="codigo {linguagem}"><code>{html.escape(texto.rstrip())}</code></pre>'


def img(args):
    caminho, _, legenda = [a.strip() for a in args.partition("|")]
    arquivo = RAIZ / caminho
    if not arquivo.exists():
        return f'<div class="caixa-pendente">{pendente("print " + caminho)}<br><small>{html.escape(legenda)}</small></div>'
    if arquivo.suffix == ".svg":
        conteudo = arquivo.read_text(encoding="utf-8")
        conteudo = re.sub(r"^<\?xml[^>]*>", "", conteudo)
    else:
        rel = Path("../..") / caminho
        conteudo = f'<img src="{rel.as_posix()}" alt="{html.escape(legenda)}">'
    return f'<figure>{conteudo}<figcaption>{html.escape(legenda)}</figcaption></figure>'


def arquivo(caminho):
    arq = RAIZ / caminho.strip()
    return bloco_codigo(arq.read_text(encoding="utf-8"), caminho.strip(), LINGUAGENS.get(arq.suffix, ""))


def codigo_servico(pasta):
    base = RAIZ / pasta.strip()
    arquivos = [base / "pom.xml", base / "Dockerfile"]
    arquivos += sorted((base / "src/main/resources").rglob("*.*"))
    arquivos += sorted((base / "src/main/java").rglob("*.java"))
    arquivos += sorted((base / "src/test").rglob("*.*"))
    partes = []
    for arq in arquivos:
        rel = arq.relative_to(RAIZ).as_posix()
        partes.append(bloco_codigo(arq.read_text(encoding="utf-8"), rel, LINGUAGENS.get(arq.suffix, "")))
    return "\n".join(partes)


def evidencia(args):
    partes = [a.strip() for a in args.split("|")]
    nome, inicio, fim = (partes + ["", ""])[:3]
    arq = RAIZ / "evidencias" / nome
    if not arq.exists():
        return f'<div class="caixa-pendente">{pendente("evidência " + nome + (" — " + inicio if inicio else ""))}</div>'
    linhas = arq.read_text(encoding="utf-8", errors="replace").splitlines()
    if inicio:
        ini = next((i for i, l in enumerate(linhas) if inicio in l), None)
        if ini is None:
            aviso = pendente("trecho '" + inicio + "' não encontrado em " + nome)
            return f'<div class="caixa-pendente">{aviso}</div>'
        linhas = linhas[ini:]
        if fim:
            f = next((i for i, l in enumerate(linhas[1:], 1) if fim in l), len(linhas))
            linhas = linhas[:f]
    return bloco_codigo("\n".join(linhas), f"evidencias/{nome}", "saida")


DIRETIVAS = {"IMG": img, "ARQUIVO": arquivo, "CODIGO": codigo_servico, "EVID": evidencia}


def processar(md_texto):
    blocos = {}

    def guardar(m):
        chave = f"@@BLOCO{len(blocos)}@@"
        blocos[chave] = DIRETIVAS[m.group(1)](m.group(2))
        return f"\n\n{chave}\n\n"

    md_texto = re.sub(r"^\[\[(IMG|ARQUIVO|CODIGO|EVID):\s*(.+?)\]\]\s*$", guardar, md_texto, flags=re.M)
    md_texto = re.sub(r"\[\[PENDENTE:\s*(.+?)\]\]", lambda m: pendente(m.group(1)), md_texto)
    corpo = markdown.markdown(md_texto, extensions=["tables", "fenced_code", "sane_lists", "attr_list", "toc"])
    for chave, valor in blocos.items():
        corpo = corpo.replace(f"<p>{chave}</p>", valor).replace(chave, valor)
    return corpo


def main():
    corpo = processar((ENTREGA / "documento.md").read_text(encoding="utf-8"))
    estilo = (ENTREGA / "estilo.css").read_text(encoding="utf-8")
    pagina = f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<title>Laboratório de Microsserviços: Alany Gabriely Lourenço da Silva</title>
<style>{estilo}</style></head><body>{corpo}</body></html>"""
    SAIDA_HTML.write_text(pagina, encoding="utf-8")
    print(f"HTML: {SAIDA_HTML}")

    chrome = next((c for c in CHROME if Path(c).exists()), None)
    if chrome:
        subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                        f"--print-to-pdf={SAIDA_PDF}", SAIDA_HTML.as_uri()],
                       check=True, capture_output=True)
        print(f"PDF:  {SAIDA_PDF}")
    if pendencias:
        print(f"\n{len(pendencias)} pendência(s) (dependem da execução no Docker):")
        for p in pendencias:
            print("  -", p)
    return 0


if __name__ == "__main__":
    sys.exit(main())
