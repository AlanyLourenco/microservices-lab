"""Gera os prints da Parte 4 em docs/prints/ a partir de execuções reais.

Prints 1, 2 e 4: trechos literais de evidencias/etapa07-teste-funcional.txt (saída real dos comandos),
renderizados como janela de terminal pelo Chrome headless. Nada é digitado à mão.
Print 3: captura real da interface do RabbitMQ (docs/prints/rabbitmq-ui-fila-pedido-criado.jpg) + log real.

Uso (com o ambiente no ar e depois do roteiro da Etapa 7): python scripts/gerar-prints.py
"""
import html
import subprocess
import sys
import tempfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
EVID = RAIZ / "evidencias" / "etapa07-teste-funcional.txt"
PRINTS = RAIZ / "docs" / "prints"
CHROME = next(p for p in [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
] if Path(p).exists())

linhas = EVID.read_text(encoding="utf-8").splitlines()


def bloco(comeca, ate=None, filtro=None):
    """Linhas desde a que começa com `comeca` até antes da próxima que começa com `ate` (ou próximo '$ ')."""
    i = next(n for n, l in enumerate(linhas) if l.startswith(comeca))
    fim = i + 1
    while fim < len(linhas) and not (linhas[fim].startswith(ate) if ate else linhas[fim].startswith("$ ")):
        fim += 1
    trecho = linhas[i:fim]
    if filtro:
        trecho = [trecho[0]] + [l for l in trecho[1:] if filtro(l)]
    while trecho and not trecho[-1].strip():
        trecho.pop()
    return trecho


def cid():
    return next(l.split()[1] for l in linhas if l.lower().startswith("x-correlation-id:"))


def tela(titulo, trechos, largura=1500):
    corpo = []
    for t in trechos:
        for l in t:
            esc = html.escape(l)
            if l.startswith("$ "):
                corpo.append(f'<span class="cmd">{esc}</span>')
            elif l.startswith("# "):
                corpo.append(f'<span class="nota">{esc}</span>')
            else:
                corpo.append(esc)
        corpo.append("")
    destaque = html.escape(cid())
    texto = "\n".join(corpo).replace(destaque, f'<span class="cid">{destaque}</span>')
    for termo in ("Pedido 2 criado", "Produto 2 reservado", "Evento publicado 2", "Pagamento aprovado 2",
                  "Pagamento rejeitado 2", "AGUARDANDO_PAGAMENTO", "HTTP/1.1 201", "PUBLICADO", "PAGO"):
        texto = texto.replace(termo, f'<b>{termo}</b>')
    return f"""<!doctype html><meta charset="utf-8"><style>
body{{margin:0;background:#1e1e1e;font:14px/1.45 Consolas,'Cascadia Mono',monospace;color:#d4d4d4;width:{largura}px}}
.barra{{background:#323233;color:#ccc;padding:7px 14px;font:13px 'Segoe UI',sans-serif}}
pre{{margin:0;padding:12px 16px;white-space:pre-wrap;word-break:break-all}}
.cmd{{color:#4ec9b0}} .nota{{color:#6a9955}} .cid{{color:#ffd866}} b{{color:#ff9e64;font-weight:600}}
</style><div class="barra">{html.escape(titulo)}</div><pre>{texto}</pre>"""


def capturar(destino, pagina_html=None, url=None, largura=1500, altura=900):
    with tempfile.TemporaryDirectory() as tmp:
        if pagina_html is not None:
            arq = Path(tmp) / "p.html"
            arq.write_text(pagina_html, encoding="utf-8")
            url = arq.as_uri()
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        f"--user-data-dir={tmp}/perfil", f"--window-size={largura},{altura}",
                        "--virtual-time-budget=8000", f"--screenshot={destino}", url],
                       check=True, capture_output=True)
    print("gerado", destino.relative_to(RAIZ))


def altura_para(trechos):
    n = sum(len(t) + 1 for t in trechos)
    extras = sum(len(l) // 150 for t in trechos for l in t)
    return 50 + 21 * (n + extras)


PRINTS.mkdir(parents=True, exist_ok=True)

# Print 1: POST /pedidos -> 201, AGUARDANDO_PAGAMENTO, X-Correlation-Id
t1 = [bloco("$ criar_pedido 2 3", "# 1.")]
capturar(PRINTS / "01-criacao-pedido.png", tela("Git Bash: criação do pedido (Etapa 7)", t1), altura=altura_para(t1))

# Print 2: estoque antes/depois, reserva com o correlationId e log do Estoque
t2 = [bloco("$ curl -s http://localhost:8081/produtos/2")[:7],
      bloco("# 1. O estoque foi atualizado?", "# 2."),
      bloco("$ logs_por_correlation", filtro=lambda l: "estoque-service" in l or "EstoqueClient" in l)]
t2[0] = ["# Antes do pedido"] + t2[0]
capturar(PRINTS / "02-reserva-estoque.png", tela("Git Bash: reserva de estoque (Etapa 7)", t2), altura=altura_para(t2))

# Print 4: log do pagamento, registro no banco do Pagamento e status atualizado
t4 = [bloco("$ logs_por_correlation", filtro=lambda l: "pagamento-service" in l or "Processado" in l or "atualizado" in l or "liberada" in l),
      bloco("# 5. O pagamento foi registrado", "# Fila"),
      bloco("# Status final do pedido", "# Reserva e estoque depois")]
capturar(PRINTS / "04-processamento-pagamento.png", tela("Git Bash: processamento do pagamento (Etapas 6, 7 e 12)", t4),
         altura=altura_para(t4))

# Print 3: captura real da interface do RabbitMQ (fila pedido.criado: consumidor e binding de pedidos.exchange),
# feita no Chrome logado e salva em docs/prints/rabbitmq-ui-fila-pedido-criado.jpg, com a linha real do log
# "Evento publicado" da Etapa 7 abaixo dela.
ui = PRINTS / "rabbitmq-ui-fila-pedido-criado.jpg"
t3 = [bloco("$ logs_por_correlation", filtro=lambda l: "Evento publicado" in l or "registrado na outbox" in l
            or "pedido.criado recebido" in l)]
pagina = tela("Git Bash: publicação do evento pedido.criado (Etapa 7)", t3).replace(
    "<div class=\"barra\">",
    f'<div class="barra">Interface de gerenciamento do RabbitMQ: http://localhost:15672/#/queues/%2F/pedido.criado</div>'
    f'<img src="{ui.as_uri()}" style="display:block;width:1500px"><div class="barra">', 1)
capturar(PRINTS / "03-publicacao-mensagem.png", pagina, altura=int(577 * 1500 / 1366) + 30 + altura_para(t3))
