#!/usr/bin/env bash
# Etapa 5: topologia do RabbitMQ (exchange, fila, binding, consumidor).
source "$(dirname "$0")/lib.sh"
{
titulo "ETAPA 5: RABBITMQ"
nota "Exchanges (pedidos.exchange e pagamentos.exchange do tipo topic; lab.dlx para mensagens mortas)"
run "docker compose exec -T rabbitmq rabbitmqctl list_exchanges -q name type durable | grep -E 'pedidos|pagamentos|lab.dlx'"
nota "Filas: mensagens prontas, não confirmadas e consumidores"
run "filas"
nota "Bindings: pedidos.exchange --pedido.criado--> fila pedido.criado"
run "docker compose exec -T rabbitmq rabbitmqctl list_bindings -q source_name destination_name routing_key | grep -v '^\s'"
nota "Consumidores conectados"
run "docker compose exec -T rabbitmq rabbitmqctl list_consumers -q queue_name channel_pid ack_required prefetch_count"
nota "Interface de gerenciamento: http://localhost:15672 (guest/guest)"
} 2>&1 | tee evidencias/etapa05-rabbitmq.txt
