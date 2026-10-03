# microservices-lab

Laboratório Avaliativo de Microsserviços: Padrões de Arquitetura de Software (UFG, 2026/2).
**Alany Gabriely Lourenço da Silva**, matrícula 202105018.

Este projeto implementa três microsserviços (Estoque, Pedido e Pagamento), cada um com o próprio PostgreSQL.
O Pedido chama o Estoque por REST. O Pedido e o Pagamento trocam eventos pelo RabbitMQ
(`pedido.criado` e `pagamento.processado`). Tudo sobe com Docker Compose.

```bash
docker compose up -d --build
curl -i -X POST http://localhost:8080/pedidos -H "Content-Type: application/json" -d '{"produtoId": 1, "quantidade": 2}'
curl http://localhost:8080/pedidos        # pedidos
curl http://localhost:8081/produtos       # estoque
# RabbitMQ: http://localhost:15672 (guest/guest)
```

| Serviço | Porta (host) | Banco | Papel |
|---|---|---|---|
| pedido-service | 8080 | pedido-db | POST/GET /pedidos; orquestra reserva → pedido → evento (outbox) |
| estoque-service | 8081 | estoque-db | GET /produtos, PUT /produtos/{id}/reservar, PUT /reservas/{cid}/liberar |
| pagamento-service | (interna) | pagamento-db | consome pedido.criado, aprova ~80%, publica pagamento.processado |

Para os experimentos de cada etapa, use `scripts/etapaXX-*.sh` (Git Bash). As saídas vão para `evidencias/`.
O documento de entrega fica em `docs/entrega/`; para regenerá-lo, rode `python docs/entrega/gerar.py`.
