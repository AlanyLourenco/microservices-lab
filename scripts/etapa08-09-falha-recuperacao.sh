#!/usr/bin/env bash
# Etapas 8 e 9: indisponibilidade do Pagamento Service e recuperação.
# Cria pedidos durante a parada até que o pedido #17 tenha sido criado nessa janela,
# para ele servir de caso no incidente investigado na Etapa 11.
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 8: SIMULAÇÃO DE FALHA"
run "docker compose stop pagamento-service"
run "docker compose ps -a pagamento-service --format 'table {{.Name}}\t{{.State}}\t{{.Status}}'"
run "filas"
run "curl -s $ESTOQUE/produtos | json"

ULTIMO=$(curl -s $PEDIDO/pedidos | python -c 'import json,sys; l=json.load(sys.stdin); print(l[-1]["id"] if l else 0)')
ALVO=$(( ULTIMO + 3 > 17 ? ULTIMO + 3 : 17 ))
ALVO=$(( ALVO < 17 ? 17 : ALVO ))
if [ "$ULTIMO" -ge 17 ]; then nota "ATENÇÃO: já existem $ULTIMO pedidos; o #17 não ficará na janela de falha"; fi
nota "Criando pedidos com o Pagamento parado (do #$((ULTIMO + 1)) ao #$ALVO)"
printf '\n%-6s %-6s %s\n' HTTP ID correlationId
PRODUTO=3
while :; do
    R=$(criar_pedido_resumo $PRODUTO 1); echo "$R"
    ID=$(echo "$R" | awk '{print $2}')
    [ "$ID" = "-" ] && break
    [ "$ID" -ge "$ALVO" ] && break
    PRODUTO=$(( PRODUTO == 3 ? 2 : 3 ))
done
sleep 3

nota "1. O pedido foi criado? (todos AGUARDANDO_PAGAMENTO)"
run "curl -s $PEDIDO/pedidos | python -c 'import json,sys; [print(p[\"id\"], p[\"status\"], p[\"correlationId\"]) for p in json.load(sys.stdin) if p[\"id\"] > $ULTIMO]'"
nota "2. O estoque foi atualizado?"
run "curl -s $ESTOQUE/produtos | json"
nota "3. O sistema inteiro parou? Pedido, Estoque e RabbitMQ seguem respondendo"
run "docker compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'"
nota "4. A mensagem foi perdida? Evidência na fila: mensagens PRONTAS, 0 consumidores"
run "filas"
nota "A mesma fila vista pela API de gerenciamento (a UI das telas). Ela atualiza as estatísticas a cada ~5 s, por isso a espera"
sleep 6
run "curl -s -u guest:guest http://localhost:15672/api/queues/%2F/pedido.criado | python -c 'import json,sys; q=json.load(sys.stdin); print({k: q.get(k) for k in (\"name\",\"durable\",\"messages_ready\",\"messages_unacknowledged\",\"consumers\")})'"
nota "Evidência nos logs: o Pedido publicou (com confirmação do broker) cada evento"
run "docker compose logs --no-color pedido-service | grep 'Evento publicado' | tail -$(( ALVO - ULTIMO ))"
nota "Pedido #17 durante a falha (o 'incidente' investigado na Etapa 11)"
run "curl -s $PEDIDO/pedidos/17 | json"
nota "Espiando a mensagem do #17 na fila sem consumi-la (ackmode=ack_requeue_true)"
run "curl -s -u guest:guest -H 'content-type: application/json' -X POST http://localhost:15672/api/queues/%2F/pedido.criado/get -d '{\"count\": 50, \"ackmode\": \"ack_requeue_true\", \"encoding\": \"auto\"}' | python -c 'import json,sys; [print(m[\"payload\"], \"| props:\", {k: m[\"properties\"].get(k) for k in (\"delivery_mode\",\"correlation_id\",\"message_id\")}) for m in json.load(sys.stdin) if \"\\\"pedidoId\\\":17,\" in m[\"payload\"]]'"
run "filas"

titulo "ETAPA 9: RECUPERAÇÃO"
nota "1. As mensagens geradas durante a indisponibilidade continuam na fila?"
run "filas"
nota "2. Iniciando novamente o Pagamento Service"
REINICIO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
run "docker compose start pagamento-service"
aguardar_saudavel pagamento-service
sleep $(( (ALVO - ULTIMO) + 5 ))
nota "3. As mensagens pendentes foram consumidas?"
run "filas"
nota "Logs do Pagamento após o reinício (desde $REINICIO)"
run "docker compose logs --no-color --since $REINICIO pagamento-service | grep -E 'recebido|aprovado|rejeitado|publicado'"
nota "4. Cada pedido foi processado?"
run "curl -s $PEDIDO/pedidos | python -c 'import json,sys; [print(p[\"id\"], p[\"status\"], p[\"atualizadoEm\"]) for p in json.load(sys.stdin) if p[\"id\"] > $ULTIMO]'"
nota "5. Registros no banco do Pagamento Service"
run "sql_pagamento \"select id, pedido_id, status, instancia, processado_em from pagamento where pedido_id > $ULTIMO order by pedido_id\""
} 2>&1 | tee evidencias/etapa08-09-falha-recuperacao.txt
