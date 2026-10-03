#!/usr/bin/env bash
# Etapa 10: duas instâncias do Pagamento Service consumindo a mesma fila.
source "$(dirname "$0")/lib.sh"
QTD=${1:-12}
{
titulo "ETAPA 10: ESCALABILIDADE"
nota "O enunciado usa 'docker compose up --scale pagamento-service=2' (em primeiro plano); aqui -d para o roteiro seguir"
run "docker compose up -d --scale pagamento-service=2"
for c in $(docker compose ps -q pagamento-service); do
    until [ "$(docker inspect -f '{{.State.Health.Status}}' "$c")" = healthy ]; do sleep 2; done
done
run "docker compose ps pagamento-service --format 'table {{.Name}}\t{{.State}}\t{{.Status}}'"
nota "Dois consumidores na fila pedido.criado"
run "filas"
run "docker compose exec -T rabbitmq rabbitmqctl list_consumers -q queue_name channel_pid prefetch_count | grep pedido.criado"

INICIO=$(curl -s $PEDIDO/pedidos | python -c 'import json,sys; l=json.load(sys.stdin); print(l[-1]["id"] if l else 0)')
nota "Criando $QTD pedidos em sequência rápida"
printf '\n%-6s %-6s %s\n' HTTP ID correlationId
PRODUTO=2
for i in $(seq 1 "$QTD"); do
    criar_pedido_resumo $PRODUTO 1
    PRODUTO=$(( PRODUTO == 2 ? 3 : 2 ))
done
sleep 12

nota "Logs: pagamento-service-1 e pagamento-service-2 (intercalados pelo horário)"
run "docker compose logs --no-color --timestamps pagamento-service | grep -E 'Pagamento (aprovado|rejeitado)' | sort -k3,3 | tail -$QTD"
nota "As mensagens foram distribuídas? Quantos pagamentos cada instância processou"
run "sql_pagamento \"select instancia, count(*) as pagamentos from pagamento where pedido_id > $INICIO group by instancia\""
run "for c in \$(docker compose ps -q pagamento-service); do echo \"\$(docker inspect -f '{{.Name}}' \$c) = hostname \$(docker inspect -f '{{.Config.Hostname}}' \$c)\"; done"
nota "Apenas uma instância processou cada mensagem? (nenhum pedido com mais de um pagamento)"
run "sql_pagamento \"select pedido_id, count(*) as vezes, string_agg(instancia, ',') as instancias from pagamento where pedido_id > $INICIO group by pedido_id order by pedido_id\""
run "sql_pagamento \"select count(*) as pedidos_com_pagamento_duplicado from (select pedido_id from pagamento group by pedido_id having count(*) > 1) d\""
run "filas"
nota "Voltando para 1 instância"
run "docker compose up -d --scale pagamento-service=1"
} 2>&1 | tee evidencias/etapa10-escalabilidade.txt
