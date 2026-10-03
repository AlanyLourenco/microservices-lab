#!/usr/bin/env bash
# Etapa 4: ambiente no Docker Compose, conectividade Pedido -> Estoque e isolamento dos bancos.
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 4: DOCKER COMPOSE"
run "docker compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}\t{{.Ports}}'"

nota "Logs confirmando que os serviços subiram"
run "docker compose logs --no-color estoque-service pedido-service pagamento-service | grep -E 'Started .*Application|Successfully applied|Schema .* is up to date'"

nota "Pedido consegue acessar Estoque? Chamada REST de dentro do contêiner do Pedido, pelo nome do serviço"
run "docker compose exec -T pedido-service curl -s -w '\nHTTP %{http_code}\n' http://estoque-service:8080/produtos"

titulo "BANCO POR SERVIÇO: ISOLAMENTO DEMONSTRADO"
nota "Cada serviço resolve o nome do PRÓPRIO banco..."
run "docker compose exec -T estoque-service getent hosts estoque-db"
run "docker compose exec -T pedido-service getent hosts pedido-db"
run "docker compose exec -T pagamento-service getent hosts pagamento-db"
nota "...mas o Pedido não alcança o banco do Estoque, e o Pagamento não alcança o banco do Pedido (redes distintas)"
run "docker compose exec -T pedido-service getent hosts estoque-db; echo \"exit=\$? (2 = nome não encontrado)\""
run "docker compose exec -T pedido-service curl -s --max-time 3 telnet://estoque-db:5432; echo \"curl exit=\$? (6 = host não resolvido; 28 = 3 s sem conectar; nos dois casos, sem acesso)\""
run "docker compose exec -T pagamento-service getent hosts pedido-db; echo \"exit=\$? (2 = nome não encontrado)\""
run "docker compose exec -T pagamento-service curl -s --max-time 3 telnet://pedido-db:5432; echo \"curl exit=\$? (6 = host não resolvido; 28 = 3 s sem conectar; nos dois casos, sem acesso)\""
nota "Redes de cada contêiner"
run "for c in \$(docker compose ps -q); do docker inspect -f '{{.Name}} -> {{range \$k, \$v := .NetworkSettings.Networks}}{{\$k}} {{end}}' \$c; done"
nota "Bancos distintos: cada um com as próprias tabelas"
run "sql_estoque '\\dt'"
run "sql_pedido '\\dt'"
run "sql_pagamento '\\dt'"
} 2>&1 | tee evidencias/etapa04-compose-isolamento.txt
