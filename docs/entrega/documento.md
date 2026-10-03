<h1 class="capa">Laboratório Avaliativo Prático:<br>Construindo uma Arquitetura de Microsserviços</h1>

<div class="capa-info">
<p>Padrões de Arquitetura de Software · 2026/2<br>Universidade Federal de Goiás</p>
<p><strong>Alany Gabriely Lourenço da Silva</strong><br>Matrícula 202105018</p>
<p>Trabalho realizado individualmente. Cobre as responsabilidades do Aluno A (Pedido Service), do Aluno B (Estoque Service) e todas as etapas conjuntas.</p>
<p>Estoque Service · Pedido Service · Pagamento Service<br>Java 17 · Spring Boot 3.5 · PostgreSQL 16 · RabbitMQ 3.13 · Docker Compose</p>
</div>

# Sumário

1. Visão geral e decisões de projeto (correções ao enunciado)
2. Parte 1: Diagrama da arquitetura
3. Parte 2: Código-fonte dos serviços
4. Parte 3: Arquivo docker-compose.yml
5. Parte 4: Prints
6. Parte 5: Respostas às perguntas de todas as etapas
7. Como executar
8. Apêndice A: Código-fonte completo
9. Apêndice B: Saídas completas dos experimentos

# 1. Visão geral e decisões de projeto

A plataforma tem três microsserviços independentes, cada um com o próprio banco PostgreSQL:

- **Estoque Service:** consulta de produtos e reserva de estoque (REST, porta 8081 no host).
- **Pedido Service:** criação e consulta de pedidos (REST, porta 8080). Orquestra o fluxo: reserva o estoque por REST e publica o evento `pedido.criado` no RabbitMQ.
- **Pagamento Service:** consome `pedido.criado`, aprova (~80%) ou rejeita (~20%) o pagamento, persiste o resultado e publica `pagamento.processado`, que o Pedido consome para atualizar o status (Etapa 12).

O enunciado aceita "modificações no projeto devido a incompletude e/ou falhas conceituais na descrição". A tabela abaixo lista **todas** as diferenças em relação à descrição original e o motivo de cada uma. Nenhuma delas altera os contratos pedidos: endpoints, códigos HTTP, mensagens de erro, nomes de exchange, fila e routing key, formatos JSON dos eventos, status do pedido e formato dos logs.

| # | Falha ou lacuna no enunciado | Consequência se implementado ao pé da letra | Correção adotada |
|---|---|---|---|
| 1 | `pedido-service` depende do estoque com `condition: service_started`. | "Iniciado" não significa "pronto". As primeiras requisições podem falhar com o Estoque ainda subindo. | Healthcheck (`/actuator/health`) nos três serviços e `condition: service_healthy`. |
| 2 | A regra da reserva é descrita como "verificar e reduzir", sem tratar concorrência. | Ler a quantidade, comparar e salvar permite que duas reservas simultâneas vendam a mesma unidade (*lost update*), sobretudo com o serviço escalado. | Reserva feita num único `UPDATE produto SET quantidade = quantidade - q WHERE id = ? AND quantidade >= q`, atômico no banco. Há também um `CHECK (quantidade >= 0)` na tabela. |
| 3 | A quantidade da reserva não é validada. | `{"quantidade": -5}` **aumentaria** o estoque. | Quantidade ≤ 0 retorna **400** e não altera o estoque. |
| 4 | A Etapa 3 pede para pensar em como desfazer a reserva, mas o Estoque não tem operação para isso. | Não há como compensar uma reserva órfã. | Toda reserva é registrada na tabela `reserva` com o `correlationId` da requisição. O endpoint `PUT /reservas/{correlationId}/liberar` desfaz a reserva de forma **idempotente**, e a reserva repetida com o mesmo correlationId não debita duas vezes. |
| 5 | O pagamento REJEITADO não devolve o estoque. | Cada pedido rejeitado "vaza" estoque para sempre. | Ao receber `pagamento.processado` com `REJEITADO`, o Pedido aciona a compensação (libera a reserva) antes de marcar o pedido como `REJEITADO`. |
| 6 | "Criar pedido" e "publicar evento" são duas escritas em sistemas diferentes (banco e broker), sem atomicidade (*dual write*). | Se o RabbitMQ estiver fora do ar ou o processo cair entre o commit e a publicação, surge um pedido que nunca será pago, sem nenhum rastro. | **Transactional Outbox:** o evento é gravado na tabela `outbox_evento` na mesma transação do pedido. Um *relay* publica os pendentes e só os marca como `PUBLICADO` após o *publisher confirm* do broker e a confirmação de que a mensagem foi roteada para uma fila (`mandatory`). |
| 7 | O RabbitMQ entrega "pelo menos uma vez", e o enunciado não trata mensagens duplicadas. | Uma reentrega (queda do consumidor antes do ack, retry) cobraria o mesmo pedido duas vezes, possivelmente com resultados diferentes. | **Consumidor idempotente:** `pedido_id UNIQUE` na tabela `pagamento`. Se o pagamento já existe, o resultado gravado é reenviado sem novo sorteio. No Pedido, a máquina de estados ignora eventos repetidos. |
| 8 | Não há tratamento de mensagens que falham sempre (*poison messages*). | Com o requeue padrão, a mensagem volta infinitamente e trava o consumidor. | 3 tentativas com backoff (1 s, 2 s). Depois disso, a mensagem vai para a *dead-letter queue* (`pedido.criado.dlq`, `pagamento.processado.dlq`), via exchange `lab.dlx`. |
| 9 | O enunciado não define o exchange do evento `pagamento.processado`. | (lacuna) | Exchange `pagamentos.exchange` (topic), de propriedade do Pagamento. A fila `pagamento.processado` é do consumidor (Pedido). |
| 10 | O banco por serviço é só uma regra de convenção ("é proibido consultar…"). Todos os contêineres ficam na mesma rede. | Nada impede tecnicamente o acesso indevido. | Uma rede Docker privada por banco: cada serviço só resolve o nome do **próprio** banco. O isolamento é demonstrável (Etapa 4). |
| 11 | O RabbitMQ não tem volume nem hostname fixo. | Recriar o contêiner apaga filas e mensagens persistentes. | `hostname: rabbitmq` e o volume `rabbitmq-data`. |
| 12 | O correlationId aparece só nos logs. | Para investigar um pedido (Etapa 11) a partir do id informado pelo usuário, seria preciso garimpar logs. | O correlationId é gravado no pedido, na reserva e no pagamento, devolvido no cabeçalho `X-Correlation-Id` e propagado em cabeçalho HTTP, corpo e propriedades AMQP. |
| 13 | A mensagem de erro 404 aparece como `"Produto inexistente?"`. | A interrogação parece erro de digitação. | Resposta `{"mensagem": "Produto inexistente"}`. |
| 14 | O `RestTemplate` não tem timeouts. | Uma lentidão do Estoque prenderia threads do Pedido indefinidamente (falha em cascata). | Timeouts de 2 s (conexão) e 5 s (leitura). Erros de rede viram **503**, com compensação preventiva. |

Além dessas correções, o compose tem **ajustes operacionais**, que não são correções ao enunciado, mas também são declarados aqui: `mem_limit: 512m` e opções enxutas da JVM (a máquina de teste tem 7,7 GB de RAM); `restart: on-failure`, para que o Pedido volte sozinho depois da queda simulada na Etapa 3; `TZ: America/Sao_Paulo`, para que logs e datas fiquem no horário local; e `PAGAMENTO_TEMPO_PROCESSAMENTO_MS=500`, um atraso proposital de 0,5 s no processamento de cada pagamento, que simula a chamada a um gateway de pagamento e torna observáveis a fila e a distribuição entre instâncias (Etapas 5, 9 e 10). A decisão aprovado/rejeitado continua sendo um sorteio com probabilidade 0,8.

Também foram adicionadas **simulações de falha controladas** para o experimento de consistência da Etapa 3. O cabeçalho `X-Simular-Falha` aceita `ERRO_APOS_RESERVA` (exceção) e `QUEDA_APOS_RESERVA` (encerra a JVM com `Runtime.halt`). Ele só tem efeito quando `LAB_SIMULACAO_FALHAS_HABILITADA=true`.

# 2. Parte 1: Diagrama da arquitetura

[[IMG: docs/diagramas/arquitetura.svg | Figura 1: Arquitetura. Três serviços, um banco por serviço em rede privada, REST síncrono entre Pedido e Estoque e eventos assíncronos via RabbitMQ.]]

[[IMG: docs/diagramas/sequencia.svg | Figura 2: Fluxo de criação de pedido, do POST até a atualização assíncrona do status (Etapas 3, 5, 6 e 12).]]

**Máquina de estados do pedido:** `AGUARDANDO_PAGAMENTO → PAGO` ou `AGUARDANDO_PAGAMENTO → REJEITADO`. Não há outras transições: um pedido decidido não muda mais, e um evento repetido é ignorado.

**Contratos de mensagens**

| Exchange (tipo) | Routing key / fila | Produtor | Consumidor | Corpo JSON |
|---|---|---|---|---|
| `pedidos.exchange` (topic) | `pedido.criado` | Pedido | Pagamento | `{"pedidoId", "produtoId", "quantidade", "correlationId"}` |
| `pagamentos.exchange` (topic) | `pagamento.processado` | Pagamento | Pedido | `{"pedidoId", "status", "correlationId"}`, com status `APROVADO` ou `REJEITADO` |
| `lab.dlx` (direct) | `pedido.criado.dlq`, `pagamento.processado.dlq` | broker (dead-letter) | inspeção manual | mensagem original |

Mensagens persistentes (`delivery_mode = 2`), filas duráveis, `correlation_id` e `message_id` preenchidos nas propriedades AMQP, `prefetch = 1` e ack automático **só após** o listener terminar sem exceção.

# 3. Parte 2: Código-fonte dos serviços

O código completo está no **Apêndice A** e na pasta `microservices-lab` entregue junto com este documento.

```
microservices-lab/
├── pedido-service/        (Aluno A)  →  pedido-db
├── estoque-service/       (Aluno B)  →  estoque-db
├── pagamento-service/                →  pagamento-db
├── docker-compose.yml
├── scripts/               roteiros dos experimentos (geram evidencias/)
└── docs/                  diagramas e este documento
```

| Serviço | Classes principais | Responsabilidade |
|---|---|---|
| Estoque | `ProdutoController`, `ReservaService`, `ProdutoRepository.debitar/creditar`, `ReservaController` | Endpoints da Etapa 1. Reserva atômica e idempotente; liberação (compensação). |
| Pedido | `PedidoController`, `PedidoService`, `EstoqueClient`, `RegistroPedido`, `OutboxRelay`, `PagamentoProcessadoListener` | Etapas 2, 3, 5, 11 e 12: orquestração (Saga), outbox, correlationId, atualização do status. |
| Pagamento | `PedidoCriadoListener`, `PagamentoService`, `DecisorPagamento`, `PagamentoProcessadoPublisher` | Etapas 6 e 12: consumo idempotente, decisão 80/20, persistência, publicação do resultado. |

**Testes automatizados** (`mvn test` em cada serviço; H2 em modo PostgreSQL com as mesmas migrations Flyway):

- **Estoque (9 testes):** dados iniciais; 200, 409 e 404 com o estoque inalterado nos erros; 400 para quantidade ≤ 0; reserva repetida não debita duas vezes; liberação devolve uma única vez. O último teste revelou um bug real durante o desenvolvimento, já corrigido: a ordem entre o UPDATE e a alteração da entidade permitia creditar duas vezes.
- **Pedido (13 testes):** ordem reservar → criar; sem estoque não cria pedido nem evento; compensação na falha após a reserva, na falha do banco e no timeout; Etapa 12 (APROVADO → PAGO; REJEITADO → libera a reserva e marca REJEITADO; evento repetido ignorado; pedido inexistente vai para a DLQ); máquina de estados; contexto completo com o pedido e o evento gravados juntos, no formato JSON da Etapa 5.
- **Pagamento (3 testes):** a taxa de aprovação fica entre 78% e 82% em 20 mil sorteios; o evento reentregue não gera segundo pagamento nem novo sorteio.

Os trechos centrais de cada regra:

**Reserva atômica (Estoque)**: `ProdutoRepository`

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update Produto p set p.quantidade = p.quantidade - :quantidade "
        + "where p.id = :id and p.quantidade >= :quantidade")
int debitar(@Param("id") Long id, @Param("quantidade") int quantidade); // 0 = insuficiente → 409
```

**Orquestração e compensação (Pedido)**: `PedidoService.criar`

```java
estoque.reservar(produtoId, quantidade, correlationId);       // 409/404 → nada é criado
try {
    simularFalhaAposReserva(simulacao, correlationId);
    pedido = registro.registrarComEvento(produtoId, quantidade, correlationId); // pedido + outbox, 1 transação
} catch (RuntimeException e) {
    compensarReserva(correlationId);                           // PUT /reservas/{cid}/liberar
    throw new PedidoNaoCriadoException();
}
log.info("correlationId={} Pedido {} criado", correlationId, pedido.getId());
```

**Consumidor idempotente (Pagamento)**: `PagamentoService.processar`

```java
Optional<Pagamento> existente = pagamentos.findByPedidoId(pedidoId);
if (existente.isPresent()) return new Resultado(existente.get(), false);   // reentrega: sem novo sorteio
Pagamento pagamento = pagamentos.save(new Pagamento(pedidoId, decisor.decidir(), correlationId, instancia));
```

# 4. Parte 3: Arquivo docker-compose.yml

As correções em relação ao compose do enunciado estão descritas nos itens 1, 10 e 11 da tabela da Seção 1, seguidas dos ajustes operacionais, e comentadas no próprio arquivo. A estrutura do compose do enunciado (serviços, imagens, portas, variáveis, healthchecks dos bancos e volumes) foi mantida.

[[ARQUIVO: docker-compose.yml]]

Cada serviço é construído pelo próprio `Dockerfile`, em dois estágios: compilação com Maven e imagem final só com a JRE.

[[ARQUIVO: pedido-service/Dockerfile]]

# 5. Parte 4: Prints

### Criação do pedido

[[IMG: docs/prints/01-criacao-pedido.png | Print 1: POST /pedidos retornando 201, status AGUARDANDO_PAGAMENTO e o cabeçalho X-Correlation-Id.]]

### Reserva de estoque

[[IMG: docs/prints/02-reserva-estoque.png | Print 2: Estoque debitado e reserva registrada com o mesmo correlationId; log "Produto {} reservado".]]

### Publicação da mensagem

[[IMG: docs/prints/03-publicacao-mensagem.png | Print 3: Interface do RabbitMQ com pedidos.exchange, a fila pedido.criado, o binding e o consumidor; log "Evento publicado".]]

### Processamento do pagamento

[[IMG: docs/prints/04-processamento-pagamento.png | Print 4: Log "Pagamento aprovado/rejeitado", registro no banco do Pagamento e status do pedido atualizado.]]

# 6. Parte 5: Respostas às perguntas

## Etapa 1: Estoque Service (verificação das regras)

As três regras foram verificadas chamando o Estoque diretamente: **200** reduz a quantidade; **409** `{"mensagem": "Estoque insuficiente"}` e **404** `{"mensagem": "Produto inexistente"}` não alteram o estoque.

[[EVID: etapa01-estoque.txt]]

## Etapa 2: Pedido Service (verificação)

Todo pedido nasce `AGUARDANDO_PAGAMENTO`: o construtor da entidade `Pedido` fixa esse status, o que o teste de contexto e a Etapa 7 confirmam. A única transição possível é para `PAGO` ou `REJEITADO` (`Pedido.registrarResultadoPagamento`). `GET /pedidos` e `GET /pedidos/{id}` aparecem nas evidências das Etapas 8, 11 e 12.

## Etapa 3: Integração REST e experimento de consistência

A regra de negócio foi verificada assim: com estoque, a reserva acontece antes da criação do pedido e o evento é gravado junto com o pedido. Sem estoque, o Pedido repassa o **409** do Estoque e as contagens de pedidos e de eventos na outbox não mudam.

[[EVID: etapa03-integracao-consistencia.txt | ETAPA 3: INTEGRAÇÃO | EXPERIMENTO DE CONSISTÊNCIA]]

O experimento foi feito em dois cenários, porque o tipo da falha muda o resultado:

- **Cenário A, queda do processo** (`QUEDA_APOS_RESERVA`, `Runtime.halt`): simula queda de energia ou `kill -9`. Nenhum `catch`/`finally` roda.
- **Cenário B, erro tratável** (`ERRO_APOS_RESERVA`): uma exceção depois da reserva, com o processo ainda vivo.

**1. O que aconteceu com o estoque?**
No cenário A, o estoque **foi debitado e permaneceu debitado**: o Teclado passou de 20 para 16, e a reserva ficou com status `RESERVADA` sem nenhum pedido correspondente (reserva órfã). O Estoque cumpriu sua parte e confirmou a transação local dele. Ele não sabe, nem tem como saber, que o Pedido morreu em seguida. No cenário B, o estoque também foi debitado, mas a compensação o devolveu logo em seguida: a reserva aparece `LIBERADA` e a quantidade volta ao valor anterior.

**2. O pedido foi criado?**
Não, em nenhum dos dois cenários. A contagem de pedidos e a busca pelo correlationId da requisição retornam zero. No cenário A o processo morreu antes do `INSERT`. No B, o cliente recebeu **500** `{"mensagem": "Falha ao criar o pedido; a reserva de estoque foi desfeita"}`. Como o evento só é gravado na outbox junto com o pedido, também não houve evento.

**3. Existe uma transação única envolvendo os dois serviços?**
Não. Cada serviço tem uma transação **local** no **próprio** banco: o Estoque fez commit do débito em `estoque-db` antes de responder 200, e o Pedido grava em `pedido-db` em outra transação, depois. A chamada HTTP não propaga contexto transacional. Uma transação distribuída (2PC/XA) exigiria um coordenador e que os dois bancos travassem recursos à espera dele. Isso acopla a disponibilidade dos serviços (se um cai, o outro fica bloqueado), contraria a autonomia dos microsserviços e não é suportado pelo RabbitMQ nem por HTTP/REST. Por isso, nesta arquitetura a consistência entre serviços é **eventual** e precisa ser construída pela aplicação.

**4. Como o sistema poderia desfazer a reserva realizada?**
Executando a **operação inversa** no serviço dono do dado: o Pedido pede ao Estoque, pela API, que libere a reserva. Nunca alterando o banco do Estoque diretamente. Foi o que implementei: cada reserva é registrada com o `correlationId` da requisição, e `PUT /reservas/{correlationId}/liberar` devolve a quantidade **uma única vez** (idempotente), o que permite repetir a chamada com segurança após um timeout. O cenário B mostra a compensação automática no log: `Compensação executada: reserva de estoque desfeita`.
O cenário A mostra o limite dessa solução: se o processo morre, o código de compensação não roda. Para cobrir esse caso, é preciso que o **estado da saga sobreviva à queda**. As alternativas são: (a) registrar a intenção em banco *antes* de reservar e ter um processo de recuperação que retome ou compense as sagas inacabadas na reinicialização; (b) uma **reconciliação** periódica no Estoque, que libere reservas `RESERVADA` sem pedido confirmado após um prazo; (c) reservas **com expiração (TTL)**, que só se tornam definitivas quando o Pedido as confirma. No experimento, a reserva órfã foi liberada manualmente pelo mesmo endpoint, que é o que uma reconciliação faria de forma automática.

**5. Que mecanismo poderia ser utilizado para realizar essa compensação?**
O padrão **Saga**: uma transação de negócio dividida em transações locais, cada uma com uma **transação compensatória**, executada em ordem inversa quando um passo posterior falha. Aqui a saga é **orquestrada**: o Pedido Service coordena os passos e dispara as compensações. Ela é usada em dois pontos: na falha da criação do pedido e no **pagamento rejeitado**, que libera a reserva (Etapa 12). Uma alternativa seria a saga **coreografada**, em que cada serviço reage a eventos (por exemplo, o Estoque consumiria `pedido.cancelado`). Para que a saga seja confiável, ela depende de mecanismos de apoio já presentes no projeto: **idempotência** (as compensações podem ser repetidas), **Transactional Outbox** (eventos não se perdem) e **retentativa com DLQ** (falhas transitórias são reprocessadas).

[[EVID: etapa03-integracao-consistencia.txt | EXPERIMENTO DE CONSISTÊNCIA]]

## Etapa 4: Docker Compose

**Pedido consegue acessar Estoque?** Sim. Pela rede `backend` do Compose, o Pedido resolve o nome `estoque-service` pelo DNS interno do Docker e chama `http://estoque-service:8080` (variável `ESTOQUE_SERVICE_URL`). O endereço é o nome do serviço, não um IP. A evidência mostra o `GET /produtos` feito de dentro do contêiner do Pedido retornando 200.

Ficou demonstrado também o **banco por serviço**: cada serviço resolve apenas o nome do próprio banco. `pedido-service` não encontra `estoque-db`, e `pagamento-service` não encontra `pedido-db` (`getent` retorna 2, "nome não encontrado"; a tentativa de conexão com `curl` à porta 5432 não se completa e termina por tempo esgotado, código 28). Os quatro requisitos de demonstração do enunciado ficam atendidos por **infraestrutura**, não só por convenção:

1. cada serviço possui seu próprio banco (três contêineres Postgres, cada um com suas tabelas);
2. o Pedido não acessa o banco do Estoque (nem consegue resolvê-lo);
3. o Pagamento não acessa o banco do Pedido (idem);
4. a comunicação entre os serviços é feita só por REST (Pedido → Estoque) e por mensagens no RabbitMQ.

[[EVID: etapa04-compose-isolamento.txt]]

## Etapa 5: RabbitMQ

[[EVID: etapa05-rabbitmq.txt]]

**1. Por que o Pedido Service publica em um Exchange em vez de enviar diretamente para uma Queue?**
Para **desacoplar o produtor dos destinos**. No AMQP, o produtor publica num exchange com uma *routing key* que descreve o fato ocorrido (`pedido.criado`), e é o broker que decide, pelas *bindings*, para quais filas a mensagem vai: zero, uma ou várias. Assim, um novo interessado no evento (notificação ao cliente, faturamento, analytics) passa a recebê-lo criando a própria fila e ligando-a ao exchange, **sem alterar uma linha do Pedido Service**. Enviar direto para uma fila amarraria o produtor ao nome da fila de um consumidor específico e exigiria mudar o produtor a cada novo consumidor. (No AMQP 0-9-1, mesmo um "envio direto para a fila" passa pelo *default exchange*; o exchange é sempre o ponto de entrada.)

**2. Qual é a diferença entre Exchange, Queue e Consumer?**
- **Exchange:** o **roteador**. Recebe as mensagens dos produtores e as encaminha às filas conforme o tipo (direct, topic, fanout, headers) e as bindings. **Não armazena** mensagens.
- **Queue:** o **buffer**. Armazena as mensagens (em disco, se duráveis e persistentes) até que sejam consumidas e confirmadas. É ela que permite ao consumidor estar offline (Etapas 8 e 9).
- **Consumer:** a **aplicação** que se inscreve numa fila, recebe as mensagens e as confirma (ack) após processá-las. Vários consumidores na mesma fila dividem o trabalho (Etapa 10).

**3. O Pedido Service sabe quem consumirá o evento?**
Não. Ele conhece apenas o exchange (`pedidos.exchange`) e a routing key (`pedido.criado`). Isso está no código: o Pedido declara só o exchange; quem declara a fila `pedido.criado` e a liga ao exchange é o **Pagamento** (`pagamento-service/.../RabbitConfig`). O Pedido também não sabe quantas instâncias consomem (Etapa 10). O único requisito do produtor, verificado com `mandatory`, é que **alguma** fila receba o evento; se nenhuma receber, ele continua pendente na outbox em vez de ser descartado.

**Observação dos valores na interface de gerenciamento:** com o Pagamento no ar, a fila `pedido.criado` tem 1 consumidor e as mensagens não se acumulam (são consumidas em cerca de meio segundo). Com o Pagamento parado (Etapa 8), o número de consumidores cai para 0 e `Ready` cresce a cada pedido. Ao religar (Etapa 9), o consumidor volta e a fila esvazia. Com `--scale` (Etapa 10), aparecem 2 consumidores.

## Etapa 6: Pagamento Service (verificação)

Ao receber `pedido.criado`, o Pagamento (1) registra o pagamento associado ao pedido, (2) sorteia aprovação com probabilidade 0,8 (`ThreadLocalRandom`, configurável por `PAGAMENTO_TAXA_APROVACAO`), (3) persiste o resultado em `pagamento-db` e (4) registra no log `Pagamento aprovado {id}` ou `Pagamento rejeitado {id}`. O serviço não faz nenhuma chamada ao Pedido: tudo de que precisa vem na mensagem, e por isso o processamento independe da disponibilidade do Pedido. A proporção observada em todos os pagamentos do experimento foi de 74,3% aprovados e 25,7% rejeitados (26 de 35 aprovados, contagem do `pagamento-db` ao fim da Etapa 12). A proporção oscila em torno de 80% porque cada pagamento é um sorteio independente; numa amostra de 35, o desvio em relação a 80% está dentro do esperado.

## Etapa 7: Teste funcional

[[EVID: etapa07-teste-funcional.txt]]

1. **O estoque foi atualizado?** Sim. Conferido logo após o 201, enquanto o pagamento ainda era processado: o Mouse passou de 50 para 47 e a reserva do correlationId do pedido está `RESERVADA`, com quantidade 3. O log do Estoque registra `Produto 2 reservado (quantidade=3, disponivel=47)`.
2. **O pedido foi criado com status AGUARDANDO_PAGAMENTO?** Sim. O `POST /pedidos` respondeu **201** com `"status":"AGUARDANDO_PAGAMENTO"`, `Location: /pedidos/2` e o cabeçalho `X-Correlation-Id` (Print 1). A consulta ao `pedido-db`, feita 4 s depois, já mostra o status final, porque a Etapa 12 atualiza o pedido em menos de um segundo.
3. **O evento pedido.criado foi publicado?** Sim. O registro na `outbox_evento` está `PUBLICADO` (1 tentativa), com o payload `{"pedidoId":2,"produtoId":2,"quantidade":3,"correlationId":…}`, e o Pedido registrou `Evento publicado 2`.
4. **O Pagamento Service consumiu o evento?** Sim. O log do Pagamento registra `Evento pedido.criado recebido: pedido 2`, com o mesmo correlationId, e a fila `pedido.criado` voltou a 0 mensagens `Ready` e 0 `Unacked` (consumida e confirmada).
5. **O pagamento foi registrado no banco do Pagamento Service?** Sim. Há uma linha em `pagamento` para o `pedido_id = 2`, com status `REJEITADO`, o mesmo correlationId e a instância que o processou.
6. **O resultado do pagamento foi registrado no log?** Sim: `Pagamento rejeitado 2`. Nesta execução o sorteio rejeitou o pagamento, o que permitiu observar também a compensação: o Pedido liberou a reserva (`Reserva liberada: produto 2 devolvido ao estoque`), o pedido terminou `REJEITADO` e o Mouse voltou a 50. O caminho aprovado aparece nas Etapas 9, 11 e 12.

## Etapa 8: Simulação de falha

[[EVID: etapa08-09-falha-recuperacao.txt | ETAPA 8 | ETAPA 9]]

**1. O pedido foi criado?** Sim. Com o Pagamento parado foram criados 15 pedidos (#3 a #17), todos com **201** e status `AGUARDANDO_PAGAMENTO`. O Pedido só depende do Estoque (REST) e do RabbitMQ (broker) para aceitar um pedido. O Pagamento não está no caminho da requisição.

**2. O estoque foi atualizado?** Sim. Mouse de 50 para 43 e Teclado de 20 para 12 (7 + 8 unidades, uma por pedido). O Notebook, que não entrou nesses pedidos, ficou em 8. A reserva é síncrona e não depende do Pagamento.

**3. O sistema inteiro parou?** Não. Apenas a **capacidade de processar pagamentos** ficou suspensa. Pedido, Estoque e RabbitMQ continuaram atendendo normalmente, e os pedidos ficaram aguardando pagamento em vez de falhar. Esse é o efeito da comunicação assíncrona: ela remove o **acoplamento temporal** entre produtor e consumidor, que não precisam estar no ar ao mesmo tempo. Se a chamada ao Pagamento fosse síncrona (REST), a queda dele derrubaria a criação de pedidos (falha em cascata).

**4. A mensagem foi perdida?** Não. As evidências:

- **Fila:** `pedido.criado` com **15** mensagens `Ready`, 0 `Unacked` e **0 consumidores** (via `rabbitmqctl` e API de gerenciamento). A leitura da fila sem consumo mostra a mensagem do pedido #17 com `delivery_mode = 2` (persistente) e o mesmo `correlation_id`.
- **Logs:** para cada pedido, o Pedido registrou `Evento publicado {id}`, e esse log só é emitido depois do *publisher confirm* do broker. Nenhuma linha do `pagamento-service` aparece no período.

Isso acontece porque a fila é **durável**, as mensagens são **persistentes** e o broker só remove uma mensagem após o **ack** do consumidor. Sem consumidor, ela simplesmente espera.

## Etapa 9: Recuperação

[[EVID: etapa08-09-falha-recuperacao.txt | ETAPA 9]]

Verificações: (1) as mensagens geradas durante a indisponibilidade continuavam na fila; (2) o Pagamento foi iniciado com `docker compose start pagamento-service`; (3) as pendentes foram consumidas e a fila voltou a 0; (4) cada pedido foi processado e mudou para `PAGO` ou `REJEITADO`; (5) cada pedido tem exatamente um registro em `pagamento-db`.

**1. O processamento precisou ser repetido manualmente?** Não. Ao subir, o Pagamento se registrou como consumidor da fila e o broker entregou automaticamente as mensagens pendentes, uma a uma (prefetch 1). Nenhum pedido foi reenviado nem reprocessado à mão.

**2. O Pedido Service precisou aguardar o Pagamento Service?** Não. Cada `POST /pedidos` respondeu 201 imediatamente durante a indisponibilidade. O Pedido só espera a confirmação do **broker**, nunca a do consumidor. A atualização para PAGO ou REJEITADO aconteceu depois, de forma assíncrona (Etapa 12).

**3. O que aconteceu com as mensagens enquanto o consumidor estava indisponível?** Ficaram armazenadas na fila `pedido.criado`, persistidas em disco pelo broker, no estado `Ready` (prontas para entrega, sem consumidor). Quando o consumidor voltou, foram entregues em ordem (FIFO) e removidas somente após o ack de cada processamento bem-sucedido.

## Etapa 10: Escalabilidade

[[EVID: etapa10-escalabilidade.txt]]

**As mensagens foram distribuídas?** Sim. Dos 12 pedidos (#18 a #29), 6 foram processados por `pagamento-service-1` e 6 por `pagamento-service-2`, alternando entre as instâncias nos logs intercalados por horário. Duas instâncias consumindo a **mesma** fila formam o padrão *competing consumers*: o RabbitMQ entrega cada mensagem a um consumidor por vez, alternando entre eles (round-robin). Com `prefetch = 1`, uma instância só recebe a próxima mensagem depois de confirmar a atual, então quem está livre pega o trabalho.

**Apenas uma instância processou cada mensagem?** Sim. A consulta por `pedido_id` mostra uma instância por pedido, e a contagem de pedidos com pagamento duplicado é **0**. A fila entrega cada mensagem a um único consumidor. Se houver reentrega (queda de uma instância antes do ack), a restrição `pedido_id UNIQUE` e a verificação prévia impedem um segundo pagamento.

**Que características da arquitetura permitem escalar só o Pagamento?**
- **Serviço sem estado (stateless):** todo o estado fica no banco e na fila, e qualquer instância processa qualquer mensagem.
- **Comunicação assíncrona por fila:** o Pedido publica no exchange sem saber quantos consumidores existem. Acrescentar instâncias não muda o produtor nem o contrato.
- **Implantação e banco independentes:** cada serviço é um contêiner separado, com o próprio banco. Escalar o Pagamento não exige escalar o Pedido ou o Estoque.
- **Sem porta fixa no host** (`expose` em vez de `ports`): várias réplicas não disputam a mesma porta, porque ninguém chama o Pagamento por HTTP.
- **Configuração externa** (variáveis de ambiente) e imagem idêntica para todas as réplicas.
- **Idempotência:** torna seguro ter várias instâncias e reentregas.

**Em quais circunstâncias o Estoque Service também precisaria ser escalado?** Quando ele virar o gargalo do caminho **síncrono**. Toda criação de pedido espera a resposta do Estoque, então a latência dele se soma à do Pedido. Isso acontece em picos de criação de pedidos (Black Friday, lançamento de produto) e com tráfego intenso de leitura do catálogo (`GET /produtos`), mesmo sem compras. Escalar o Pedido sem escalar o Estoque só transfere a fila de espera para ele. Também seria preciso escalar por **disponibilidade**: com uma instância só, a queda do Estoque impede a criação de qualquer pedido (o Pedido responde 503). Para escalar o Estoque seria necessário um balanceador ou API gateway na frente das réplicas (a porta 8081 fixa no host impede duas réplicas). A consistência já está garantida, porque a reserva é um `UPDATE` atômico no banco e não depende de estado em memória. Num volume maior, o gargalo passa a ser o próprio `estoque-db`, com contenção de linhas nos produtos mais vendidos, tratável com réplicas de leitura para consultas e particionamento.

## Etapa 11: Observabilidade e investigação do incidente

**Implementação:** o Pedido gera um `UUID.randomUUID()` por requisição (`PedidoController`) e o propaga no cabeçalho `X-Correlation-Id` para o Estoque, no corpo e nas propriedades AMQP (`correlation_id` e o header `correlationId`) dos dois eventos. Também o grava em `pedido`, `reserva` e `pagamento` e o devolve ao cliente no cabeçalho da resposta. Os logs seguem exatamente os formatos do enunciado:

| Serviço | Log (formato do enunciado) |
|---|---|
| Pedido | `correlationId={} Pedido {} criado` |
| Estoque | `correlationId={} Produto {} reservado` (seguido de `(quantidade=…, disponivel=…)`) |
| RabbitMQ (relay do Pedido) | `correlationId={} Evento publicado {}` |
| Pagamento | `correlationId={} Pagamento aprovado {}` / `Pagamento rejeitado {}` |

**Investigação do Pedido #17.** O pedido #17 foi criado durante a parada do Pagamento (Etapa 8), no momento em que o usuário relatou que ele "não foi concluído". O procedimento: (1) `GET /pedidos/17` fornece o `correlationId`; (2) `grep` desse id nos logs dos três serviços; (3) ordenação das linhas pelo horário; (4) conferência nos bancos e na fila.

[[EVID: etapa11-incidente-pedido-17.txt]]

1. **O pedido foi criado?** Sim. `Pedido 17 criado` às 09:36:03.105, 5 ms depois de o evento ser gravado na outbox.
2. **O estoque foi reservado?** Sim. `Produto 3 reservado (quantidade=1, disponivel=12)` às 09:36:03.088 no estoque-service, com o mesmo correlationId. A reserva consta como `RESERVADA` (o pagamento foi aprovado depois e ela foi mantida).
3. **O evento pedido.criado foi publicado?** Sim. `Evento publicado 17` às 09:36:03.582, com o registro da outbox `PUBLICADO` na primeira tentativa.
4. **O Pagamento Service recebeu o evento?** Não no momento do relato. O pagamento-service estava parado desde 09:35:56 (`Graceful shutdown complete`). A primeira linha dele com esse correlationId, `Evento pedido.criado recebido: pedido 17`, só aparece às 09:36:29.165, depois do reinício (`Started PagamentoApplication` às 09:36:21).
5. **O pagamento foi processado?** Não enquanto o serviço estava parado. Depois do reinício, sim: `Pagamento aprovado 17` às 09:36:29.679, e o pedido foi atualizado para `PAGO` às 09:36:29.691.
6. **Em qual etapa ocorreu o problema?** No **consumo do evento pelo Pagamento Service**: o consumidor estava indisponível (parado). Os passos anteriores (criação, reserva, publicação) foram concluídos com sucesso.
7. **Qual evidência nos logs permite identificar a etapa da falha?** A linha do tempo do correlationId termina em `Evento publicado 17` no pedido-service, e **não há nenhuma linha** com esse correlationId no pagamento-service até o reinício. Os logs de ciclo de vida do pagamento-service mostram o desligamento antes da criação do pedido e o `Started PagamentoApplication` depois. O intervalo entre `Evento publicado` e `Evento pedido.criado recebido` (25,6 s, de 09:36:03.582 a 09:36:29.165) é explicado pela indisponibilidade: o serviço só voltou às 09:36:21.701, e depois ainda consumiu, em ordem FIFO e a cerca de 0,5 s cada, as 14 mensagens dos pedidos #3 a #16, que estavam na fila antes da do #17.
8. **O que aconteceu com a mensagem no RabbitMQ?** Ficou **retida** na fila `pedido.criado` como `Ready`, sem consumidores, persistida (`delivery_mode = 2`). A evidência da Etapa 8 mostra a própria mensagem do #17 na fila. Ela foi entregue quando o Pagamento voltou e removida após o ack. Não houve perda nem necessidade de reenvio manual.

**Leitura da ordem dos logs.** Em fluxos normais (Etapas 7 e 12), a linha `Evento pedido.criado recebido` do Pagamento às vezes aparece alguns milissegundos *antes* de `Evento publicado` no Pedido. Isso não é um erro: o relay da outbox só registra `Evento publicado` depois de receber o *publisher confirm* do broker, e nesse intervalo o broker já pode ter entregue a mensagem ao consumidor. O log marca o momento em que a publicação ficou **garantida**, não o instante do envio.

Sem o correlationId, a mesma investigação exigiria correlacionar logs por horário aproximado e por ids diferentes em cada serviço: o Estoque nem conhece o id do pedido, já que a reserva acontece antes de o pedido existir. Com ele, um único `grep` reconstrói o fluxo inteiro.

## Etapa 12: Atualização assíncrona do pedido

[[EVID: etapa12-atualizacao-status.txt]]

O Pagamento publica `{"pedidoId", "status": "APROVADO" | "REJEITADO", "correlationId"}` em `pagamentos.exchange` com a routing key `pagamento.processado`, depois de persistir o resultado e com confirmação do broker. O Pedido consome a fila `pagamento.processado` e aplica a transição: `APROVADO → PAGO` e `REJEITADO → REJEITADO`. No caso rejeitado, ele antes libera a reserva de estoque (compensação). O Pagamento **não acessa** o banco do Pedido: a única comunicação é o evento.

**Teste:** foram criados 6 pedidos (#30 a #35) até observar os dois desfechos; o pedido #30 terminou `PAGO` e o #35, `REJEITADO`. Nos dois casos, o status no `pedido-db` coincide com o resultado no `pagamento-db`. No caso rejeitado, a reserva do correlationId aparece `LIBERADA` e a quantidade voltou ao estoque.

**Garantias:** se o Pedido estiver fora do ar, o evento espera na fila `pagamento.processado` (mesmo mecanismo das Etapas 8 e 9). Um evento repetido não altera um pedido já decidido. Um evento de pedido inexistente ou com status desconhecido vai para `pagamento.processado.dlq`.

# 7. Como executar

Pré-requisitos: Docker Desktop (WSL 2) e, para os roteiros, Git Bash e Python.

```bash
cd microservices-lab
docker compose up -d --build          # sobe tudo; aguarde os serviços ficarem "healthy"
docker compose ps

curl -i -X POST http://localhost:8080/pedidos -H "Content-Type: application/json" \
     -d '{"produtoId": 1, "quantidade": 2}'
curl http://localhost:8080/pedidos
curl http://localhost:8081/produtos
# RabbitMQ: http://localhost:15672 (guest / guest)

# roteiros dos experimentos (salvam a saída em evidencias/)
bash scripts/etapa01-estoque.sh
bash scripts/etapa03-integracao-consistencia.sh
bash scripts/etapa04-compose-isolamento.sh
bash scripts/etapa05-rabbitmq.sh
bash scripts/etapa07-teste-funcional.sh
bash scripts/etapa08-09-falha-recuperacao.sh
bash scripts/etapa10-escalabilidade.sh
bash scripts/etapa11-incidente.sh 17
bash scripts/etapa12-atualizacao-status.sh

docker compose down -v                # remove contêineres e volumes
```

# Apêndice A: Código-fonte completo

## A.1 Estoque Service

[[CODIGO: estoque-service]]

## A.2 Pedido Service

[[CODIGO: pedido-service]]

## A.3 Pagamento Service

[[CODIGO: pagamento-service]]

## A.4 Roteiros de experimento

[[ARQUIVO: scripts/lib.sh]]

# Apêndice B: Saídas completas dos experimentos

As saídas abaixo foram geradas pelos roteiros da pasta `scripts/`, executados em sequência num ambiente recém-criado (`docker compose down -v` e depois `up`).

[[EVID: etapa01-estoque.txt]]
[[EVID: etapa03-integracao-consistencia.txt]]
[[EVID: etapa04-compose-isolamento.txt]]
[[EVID: etapa05-rabbitmq.txt]]
[[EVID: etapa07-teste-funcional.txt]]
[[EVID: etapa08-09-falha-recuperacao.txt]]
[[EVID: etapa10-escalabilidade.txt]]
[[EVID: etapa11-incidente-pedido-17.txt]]
[[EVID: etapa12-atualizacao-status.txt]]
