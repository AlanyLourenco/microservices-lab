#!/usr/bin/env bash
# Funções comuns aos roteiros de experimento. Use com: source scripts/lib.sh
# Requisitos: Git Bash (ou Linux/macOS), curl, python e Docker Desktop em execução.

set -u
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

PEDIDO=http://localhost:8080
ESTOQUE=http://localhost:8081
mkdir -p evidencias

titulo() { printf '\n\n######## %s ########\n' "$*"; }
nota()   { printf '\n# %s\n' "$*"; }

# Mostra o comando e em seguida a saída real dele.
run() {
    printf '\n$ %s\n' "$*"
    eval "$@"
    printf '\n'
}

# Formata JSON da entrada padrão (sem depender de jq).
json() { python -c 'import json,sys; d=sys.stdin.read(); print(json.dumps(json.loads(d), indent=2, ensure_ascii=False) if d.strip() else "")'; }

# POST /pedidos mostrando status HTTP, cabeçalhos relevantes e corpo.
# uso: criar_pedido <produtoId> <quantidade> [X-Simular-Falha]
criar_pedido() {
    local extra=()
    [ -n "${3:-}" ] && extra=(-H "X-Simular-Falha: $3")
    curl -s -i -X POST "$PEDIDO/pedidos" -H 'Content-Type: application/json' "${extra[@]}" \
        -d "{\"produtoId\": $1, \"quantidade\": $2}" | tr -d '\r' | grep -Ev '^(Date|Transfer-Encoding|Keep-Alive|Connection|Vary):'
}

# POST /pedidos silencioso; imprime "<http> <id ou -> <correlationId>".
criar_pedido_resumo() {
    local saida
    saida=$(curl -s -i -X POST "$PEDIDO/pedidos" -H 'Content-Type: application/json' \
        -d "{\"produtoId\": $1, \"quantidade\": $2}" | tr -d '\r')
    local http cid id
    http=$(printf '%s\n' "$saida" | head -1 | awk '{print $2}')
    cid=$(printf '%s\n' "$saida" | grep -i '^X-Correlation-Id:' | awk '{print $2}')
    id=$(printf '%s\n' "$saida" | tail -1 | python -c 'import json,sys
try: print(json.load(sys.stdin).get("id","-"))
except Exception: print("-")')
    echo "$http $id $cid"
}

get() { curl -s "$1" | json; }

# Estado das filas direto no broker (rabbitmqctl é instantâneo; a API de gerenciamento
# atualiza as estatísticas a cada ~5 s).
filas() {
    docker compose exec -T rabbitmq rabbitmqctl list_queues -q name messages_ready messages_unacknowledged consumers \
        | tr -d '\r' | python -c 'import sys
linhas = [l.split() for l in sys.stdin if l.strip()]
for l in linhas: print("".join(c.ljust(28) for c in l))'
}

sql_estoque()   { docker compose exec -T estoque-db   psql -U estoque   -d estoque   -c "$1"; }
sql_pedido()    { docker compose exec -T pedido-db    psql -U pedido    -d pedido    -c "$1"; }
sql_pagamento() { docker compose exec -T pagamento-db psql -U pagamento -d pagamento -c "$1"; }

# Espera um serviço responder UP no /actuator/health (de dentro do contêiner).
aguardar_saudavel() {
    local servico=$1 i
    for i in $(seq 1 90); do
        if docker compose exec -T "$servico" curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
            echo "$servico saudável"
            return 0
        fi
        sleep 2
    done
    echo "TIMEOUT aguardando $servico" >&2
    return 1
}

# Linhas de log de todos os serviços que mencionam um correlationId, em ordem de tempo.
logs_por_correlation() {
    docker compose logs --no-color --timestamps pedido-service estoque-service pagamento-service 2>/dev/null \
        | grep -F "$1" | sort -k3,3
}
