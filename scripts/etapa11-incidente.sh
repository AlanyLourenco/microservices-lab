#!/usr/bin/env bash
# Etapa 11: reconstrução do fluxo de um pedido a partir dos logs (padrão: pedido #17).
source "$(dirname "$0")/lib.sh"
ID=${1:-17}
{
titulo "ETAPA 11: INVESTIGAÇÃO DO PEDIDO #$ID"
nota "Ponto de partida: o pedidoId informado pelo usuário. O Pedido Service devolve o correlationId."
run "curl -s $PEDIDO/pedidos/$ID | json"
CID=$(curl -s $PEDIDO/pedidos/$ID | python -c 'import json,sys; print(json.load(sys.stdin).get("correlationId",""))')

nota "1. O pedido foi criado?"
run "docker compose logs --no-color --timestamps pedido-service | grep -F \"correlationId=$CID\" | grep -E 'recebida|criado'"
nota "2. O estoque foi reservado?"
run "docker compose logs --no-color --timestamps estoque-service | grep -F \"correlationId=$CID\""
run "curl -s $ESTOQUE/reservas/$CID | json"
nota "3. O evento pedido.criado foi publicado?"
run "docker compose logs --no-color --timestamps pedido-service | grep -F \"correlationId=$CID\" | grep -E 'outbox|Evento publicado'"
run "sql_pedido \"select id, pedido_id, status, tentativas, criado_em, publicado_em from outbox_evento where pedido_id = $ID\""
nota "4. O Pagamento Service recebeu o evento?  5. O pagamento foi processado?"
run "docker compose logs --no-color --timestamps pagamento-service | grep -F \"correlationId=$CID\""
run "sql_pagamento \"select * from pagamento where pedido_id = $ID\""
nota "Retorno do resultado ao Pedido (Etapa 12)"
run "docker compose logs --no-color --timestamps pedido-service | grep -F \"correlationId=$CID\" | grep -E 'pagamento.processado|atualizado|liberada'"

nota "Linha do tempo completa (três serviços, ordenada pelo horário)"
run "logs_por_correlation $CID"
nota "Ciclo de vida do Pagamento Service no período (parada e retorno)"
run "docker compose logs --no-color --timestamps pagamento-service | grep -E 'Started PagamentoApplication|Stopping|Shutting down|Graceful shutdown|Closing JPA' | head -20"
nota "8. Situação da fila (a evidência da fila DURANTE a falha está em evidencias/etapa08-09-falha-recuperacao.txt)"
run "filas"
} 2>&1 | tee evidencias/etapa11-incidente-pedido-$ID.txt
