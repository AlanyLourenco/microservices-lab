#!/usr/bin/env bash
# Etapa 12: atualização assíncrona do status do pedido (PAGO / REJEITADO).
# Cria pedidos até observar pelo menos um aprovado e um rejeitado.
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 12: ATUALIZAÇÃO ASSÍNCRONA DO PEDIDO"
run "filas"
INICIO=$(curl -s $PEDIDO/pedidos | python -c 'import json,sys; l=json.load(sys.stdin); print(l[-1]["id"] if l else 0)')
run "curl -s $ESTOQUE/produtos | json"

PAGO=0; REJ=0; N=0
printf '\n%-6s %-6s %-38s %s\n' HTTP ID correlationId "status final"
while [ $PAGO -eq 0 ] || [ $REJ -eq 0 ]; do
    R=$(criar_pedido_resumo 2 1)
    ID=$(echo "$R" | awk '{print $2}')
    [ "$ID" = "-" ] && { echo "falha ao criar pedido: $R"; break; }
    sleep 2
    ST=$(curl -s $PEDIDO/pedidos/$ID | python -c 'import json,sys; print(json.load(sys.stdin)["status"])')
    echo "$R $ST"
    [ "$ST" = PAGO ] && PAGO=$((PAGO + 1))
    [ "$ST" = REJEITADO ] && REJ=$((REJ + 1))
    N=$((N + 1)); [ $N -ge 30 ] && break
done

PID_PAGO=$(curl -s $PEDIDO/pedidos | python -c "import json,sys; print([p['id'] for p in json.load(sys.stdin) if p['id'] > $INICIO and p['status']=='PAGO'][0])")
PID_REJ=$(curl -s $PEDIDO/pedidos | python -c "import json,sys; print([p['id'] for p in json.load(sys.stdin) if p['id'] > $INICIO and p['status']=='REJEITADO'][0])")

nota "Caso APROVADO -> PAGO (pedido #$PID_PAGO)"
CID=$(curl -s $PEDIDO/pedidos/$PID_PAGO | python -c 'import json,sys; print(json.load(sys.stdin)["correlationId"])')
run "curl -s $PEDIDO/pedidos/$PID_PAGO | json"
run "logs_por_correlation $CID"
run "curl -s $ESTOQUE/reservas/$CID | json"

nota "Caso REJEITADO -> REJEITADO (pedido #$PID_REJ); a reserva é compensada (LIBERADA)"
CID=$(curl -s $PEDIDO/pedidos/$PID_REJ | python -c 'import json,sys; print(json.load(sys.stdin)["correlationId"])')
run "curl -s $PEDIDO/pedidos/$PID_REJ | json"
run "logs_por_correlation $CID"
run "curl -s $ESTOQUE/reservas/$CID | json"

nota "Conferência cruzada (cada banco consultado pelo próprio serviço/contêiner)"
run "sql_pedido \"select id, status from pedido where id > $INICIO order by id\""
run "sql_pagamento \"select pedido_id, status from pagamento where pedido_id > $INICIO order by pedido_id\""
nota "Totais gerais: proporção de aprovação observada"
run "sql_pagamento \"select status, count(*), round(100.0 * count(*) / sum(count(*)) over (), 1) as percentual from pagamento group by status\""
run "sql_pedido \"select status, count(*) from pedido group by status order by status\""
run "curl -s $ESTOQUE/produtos | json"
run "filas"
} 2>&1 | tee evidencias/etapa12-atualizacao-status.txt
