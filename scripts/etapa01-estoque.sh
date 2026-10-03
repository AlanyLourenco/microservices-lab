#!/usr/bin/env bash
# Etapa 1: endpoints e regras do Estoque Service, chamados diretamente (sem o Pedido).
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 1: ESTOQUE SERVICE"

nota "Endpoint 1: consultar todos os produtos (dados iniciais: Notebook 10, Mouse 50, Teclado 20)"
run "curl -s $ESTOQUE/produtos | json"

nota "Endpoint 2: consultar produto"
run "curl -s $ESTOQUE/produtos/1 | json"
run "curl -s -w '\nHTTP %{http_code}\n' $ESTOQUE/produtos/99"

nota "Endpoint 3, regra 1: produto existe e há quantidade suficiente -> reduz e retorna 200"
run "curl -s -w '\nHTTP %{http_code}\n' -X PUT $ESTOQUE/produtos/2/reservar -H 'Content-Type: application/json' -d '{\"quantidade\": 2}'"
run "curl -s $ESTOQUE/produtos/2 | json"

nota "Regra 2: quantidade maior que a disponível -> 409 e o estoque NÃO muda"
run "curl -s -w '\nHTTP %{http_code}\n' -X PUT $ESTOQUE/produtos/2/reservar -H 'Content-Type: application/json' -d '{\"quantidade\": 1000}'"
run "curl -s $ESTOQUE/produtos/2 | json"

nota "Regra 3: produto inexistente -> 404 e o estoque NÃO muda"
run "curl -s -w '\nHTTP %{http_code}\n' -X PUT $ESTOQUE/produtos/99/reservar -H 'Content-Type: application/json' -d '{\"quantidade\": 1}'"

nota "Correção: quantidade negativa ou zero -> 400 (sem isso, -5 AUMENTARIA o estoque)"
run "curl -s -w '\nHTTP %{http_code}\n' -X PUT $ESTOQUE/produtos/2/reservar -H 'Content-Type: application/json' -d '{\"quantidade\": -5}'"
run "curl -s $ESTOQUE/produtos/2 | json"

nota "Devolvendo as 2 unidades reservadas acima, para não interferir nas próximas etapas"
CID=$(curl -s $ESTOQUE/reservas | python -c 'import json,sys; print(json.load(sys.stdin)[-1]["correlationId"])')
run "curl -s -X PUT $ESTOQUE/reservas/$CID/liberar | json"
run "curl -s $ESTOQUE/produtos/2 | json"
} 2>&1 | tee evidencias/etapa01-estoque.txt
