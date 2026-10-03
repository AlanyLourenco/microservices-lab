#!/usr/bin/env bash
# Etapa 7: teste funcional ponta a ponta de um pedido.
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 7: TESTE FUNCIONAL"
run "curl -s $ESTOQUE/produtos/2 | json"
run "filas"

nota "POST /pedidos"
SAIDA=$(criar_pedido 2 3)
printf '\n$ criar_pedido 2 3\n%s\n' "$SAIDA"
CID=$(printf '%s\n' "$SAIDA" | grep -i '^X-Correlation-Id:' | awk '{print $2}')
ID=$(printf '%s\n' "$SAIDA" | tail -1 | python -c 'import json,sys; print(json.load(sys.stdin)["id"])')

nota "1. O estoque foi atualizado? (50 -> 47) Conferido logo após o 201, enquanto o pagamento ainda é processado"
run "curl -s $ESTOQUE/produtos/2 | json"
run "curl -s $ESTOQUE/reservas/$CID | json"
sleep 4

nota "2. O pedido foi criado com status AGUARDANDO_PAGAMENTO? (ver corpo do 201 acima; abaixo o registro no banco)"
run "sql_pedido \"select id, produto_id, quantidade, status, correlation_id, criado_em, atualizado_em from pedido where id = $ID\""
nota "3. O evento pedido.criado foi publicado? (outbox PUBLICADO + log 'Evento publicado')"
run "sql_pedido \"select id, pedido_id, routing_key, status, tentativas, payload, publicado_em from outbox_evento where pedido_id = $ID\""
nota "4. O Pagamento Service consumiu o evento?  6. O resultado foi registrado no log?"
run "logs_por_correlation $CID"
nota "5. O pagamento foi registrado no banco do Pagamento Service?"
run "sql_pagamento \"select * from pagamento where pedido_id = $ID\""
nota "Fila esvaziada: mensagem consumida e confirmada (ack)"
run "filas"
nota "Status final do pedido (atualizado pela Etapa 12)"
run "curl -s $PEDIDO/pedidos/$ID | json"
nota "Reserva e estoque depois do pagamento (APROVADO: reserva mantida; REJEITADO: reserva LIBERADA pela compensação)"
run "curl -s $ESTOQUE/reservas/$CID | json"
run "curl -s $ESTOQUE/produtos/2 | json"
} 2>&1 | tee evidencias/etapa07-teste-funcional.txt
