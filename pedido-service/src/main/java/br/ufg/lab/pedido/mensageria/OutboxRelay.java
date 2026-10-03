package br.ufg.lab.pedido.mensageria;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Relay da Transactional Outbox: publica no RabbitMQ os eventos gravados junto com os
 * pedidos. Um evento só é marcado como PUBLICADO depois que o broker confirma
 * (publisher confirm) que o aceitou e o roteou para alguma fila. Se o RabbitMQ estiver
 * fora do ar, ou se ainda não houver fila ligada ao exchange, o evento continua
 * PENDENTE e é reenviado depois. Nada se perde.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration ESPERA_CONFIRMACAO = Duration.ofSeconds(5);
    private static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(5);

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transacao;
    private final ObjectMapper objectMapper;

    private volatile Instant proximaTentativa = Instant.EPOCH;

    public OutboxRelay(OutboxRepository outbox, RabbitTemplate rabbitTemplate, TransactionTemplate transacao,
                       ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.transacao = transacao;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.intervalo-ms:500}")
    public void publicarPendentes() {
        if (Instant.now().isBefore(proximaTentativa)) {
            return;
        }
        // Um evento por transação, em ordem de criação. Para no primeiro que falhar,
        // para preservar a ordem dos eventos.
        while (Boolean.TRUE.equals(transacao.execute(status -> publicarProximo()))) {
            // continua enquanto houver eventos publicados com sucesso
        }
    }

    private boolean publicarProximo() {
        Optional<OutboxEvento> pendente = outbox.travarProximoPendente();
        if (pendente.isEmpty()) {
            return false;
        }
        OutboxEvento evento = pendente.get();
        try {
            publicar(evento);
            evento.marcarPublicado();
            outbox.save(evento);
            // Log da Etapa 11 ("RabbitMQ")
            log.info("correlationId={} Evento publicado {}", evento.getCorrelationId(), evento.getPedidoId());
            return true;
        } catch (Exception e) {
            evento.registrarFalha();
            outbox.save(evento);
            proximaTentativa = Instant.now().plus(ESPERA_APOS_FALHA);
            log.warn("correlationId={} Evento {} do pedido {} não publicado (tentativa {}): {}. "
                            + "Permanece PENDENTE na outbox; nova tentativa em {}s",
                    evento.getCorrelationId(), evento.getRoutingKey(), evento.getPedidoId(), evento.getTentativas(),
                    e.getMessage(), ESPERA_APOS_FALHA.toSeconds());
            return false;
        }
    }

    private void publicar(OutboxEvento evento) throws Exception {
        PedidoCriadoEvento payload = objectMapper.readValue(evento.getPayload(), PedidoCriadoEvento.class);
        CorrelationData confirmacao = new CorrelationData(String.valueOf(evento.getId()));

        rabbitTemplate.convertAndSend(evento.getExchange(), evento.getRoutingKey(), payload, mensagem -> {
            mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            mensagem.getMessageProperties().setCorrelationId(evento.getCorrelationId());
            mensagem.getMessageProperties().setMessageId("pedido.criado-" + evento.getPedidoId());
            mensagem.getMessageProperties().setHeader("correlationId", evento.getCorrelationId());
            return mensagem;
        }, confirmacao);

        CorrelationData.Confirm confirm = confirmacao.getFuture()
                .get(ESPERA_CONFIRMACAO.toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("broker recusou a mensagem (nack): " + confirm.getReason());
        }
        if (confirmacao.getReturned() != null) {
            throw new IllegalStateException("nenhuma fila ligada a " + evento.getExchange() + " com a routing key "
                    + evento.getRoutingKey() + " (mensagem devolvida pelo broker)");
        }
    }
}
