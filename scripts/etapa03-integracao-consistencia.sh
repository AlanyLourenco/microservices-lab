#!/usr/bin/env bash
# Etapa 3: integração REST Pedido -> Estoque e experimento de consistência.
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 3: INTEGRAÇÃO REST E REGRA DE NEGÓCIO"

nota "Estoque do Notebook (produto 1) antes"
run "curl -s $ESTOQUE/produtos/1 | json"

nota "COM estoque: reservar -> criar pedido -> publicar evento. Resposta 201, status AGUARDANDO_PAGAMENTO"
run "criar_pedido 1 2"
run "curl -s $ESTOQUE/produtos/1 | json"

nota "SEM estoque: não cria pedido, não publica evento, retorna erro (409 vindo do Estoque)"
run "sql_pedido 'select count(*) as pedidos from pedido'"
run "sql_pedido 'select count(*) as eventos_outbox from outbox_evento'"
run "criar_pedido 1 999"
run "sql_pedido 'select count(*) as pedidos from pedido'"
run "sql_pedido 'select count(*) as eventos_outbox from outbox_evento'"

nota "Produto inexistente: 404, nenhum pedido criado"
run "criar_pedido 99 1"

titulo "EXPERIMENTO DE CONSISTÊNCIA: FALHA APÓS A RESERVA E ANTES DA CRIAÇÃO DO PEDIDO"

nota "Cenário A: o processo do Pedido Service CAI (Runtime.halt) logo após a reserva"
run "curl -s $ESTOQUE/produtos/3 | json"
run "sql_pedido 'select count(*) as pedidos from pedido'"
run "criar_pedido 3 4 QUEDA_APOS_RESERVA; echo \"(curl exit=\$?: conexão encerrada sem resposta)\""
sleep 2
nota "O Estoque foi debitado?"
run "curl -s $ESTOQUE/produtos/3 | json"
CID_QUEDA=$(docker compose logs --no-color pedido-service | grep 'Queda do processo' | tail -1 | grep -o 'correlationId=[^ ]*' | cut -d= -f2)
nota "Reserva órfã no Estoque (correlationId $CID_QUEDA)"
run "curl -s $ESTOQUE/reservas/$CID_QUEDA | json"
nota "O pedido foi criado? (contagem e busca pelo correlationId)"
run "docker compose ps pedido-service"
aguardar_saudavel pedido-service
run "sql_pedido \"select count(*) as pedidos, count(*) filter (where correlation_id = '$CID_QUEDA') as pedidos_deste_cid from pedido\""
nota "Logs do incidente: o Estoque reservou e o Pedido morreu antes de gravar. Nenhuma compensação rodou."
run "logs_por_correlation $CID_QUEDA"
run "docker compose logs --no-color pedido-service | grep -E 'exited|Started PedidoApplication' | tail -3"
nota "Compensação manual (o que uma Saga/reconciliação faria automaticamente): liberar a reserva órfã"
run "curl -s -X PUT $ESTOQUE/reservas/$CID_QUEDA/liberar | json"
run "curl -s $ESTOQUE/produtos/3 | json"

nota "Cenário B: ERRO (exceção) após a reserva. O processo segue vivo e executa a compensação."
run "curl -s $ESTOQUE/produtos/3 | json"
SAIDA=$(criar_pedido 3 4 ERRO_APOS_RESERVA)
printf '\n$ criar_pedido 3 4 ERRO_APOS_RESERVA\n%s\n' "$SAIDA"
CID_ERRO=$(printf '%s\n' "$SAIDA" | grep -i '^X-Correlation-Id:' | awk '{print $2}')
run "curl -s $ESTOQUE/produtos/3 | json"
run "curl -s $ESTOQUE/reservas/$CID_ERRO | json"
run "sql_pedido \"select count(*) as pedidos_deste_cid from pedido where correlation_id = '$CID_ERRO'\""
run "logs_por_correlation $CID_ERRO"
} 2>&1 | tee evidencias/etapa03-integracao-consistencia.txt
