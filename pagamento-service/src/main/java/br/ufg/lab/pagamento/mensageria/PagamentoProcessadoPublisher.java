package br.ufg.lab.pagamento.mensageria;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import br.ufg.lab.pagamento.pagamento.Pagamento;

@Component
public class PagamentoProcessadoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PagamentoProcessadoPublisher.class);
    private static final long ESPERA_CONFIRMACAO_MS = 5000;

    private final RabbitTemplate rabbitTemplate;

    public PagamentoProcessadoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publica o resultado e espera a confirmação do broker. Se a publicação falhar, a
     * exceção sobe até o listener: a mensagem pedido.criado não recebe ack e é
     * reentregue, e o reprocessamento (idempotente) publica o mesmo resultado de novo.
     * Assim, o resultado gravado no banco sempre acaba chegando ao Pedido Service.
     */
    public void publicar(Pagamento pagamento) {
        PagamentoProcessadoEvento evento = new PagamentoProcessadoEvento(
                pagamento.getPedidoId(), pagamento.getStatus().name(), pagamento.getCorrelationId());
        CorrelationData confirmacao = new CorrelationData("pagamento-" + pagamento.getPedidoId());

        rabbitTemplate.convertAndSend(RabbitConfig.PAGAMENTOS_EXCHANGE, RabbitConfig.PAGAMENTO_PROCESSADO, evento,
                mensagem -> {
                    mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    mensagem.getMessageProperties().setCorrelationId(pagamento.getCorrelationId());
                    mensagem.getMessageProperties().setMessageId("pagamento.processado-" + pagamento.getPedidoId());
                    mensagem.getMessageProperties().setHeader("correlationId", pagamento.getCorrelationId());
                    return mensagem;
                }, confirmacao);

        try {
            CorrelationData.Confirm confirm = confirmacao.getFuture().get(ESPERA_CONFIRMACAO_MS, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("broker recusou pagamento.processado (nack): " + confirm.getReason());
            }
            if (confirmacao.getReturned() != null) {
                throw new AmqpException("nenhuma fila ligada a " + RabbitConfig.PAGAMENTOS_EXCHANGE
                        + " com a routing key " + RabbitConfig.PAGAMENTO_PROCESSADO);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("interrompido aguardando confirmação do broker", e);
        } catch (AmqpException e) {
            throw e;
        } catch (Exception e) {
            throw new AmqpException("sem confirmação do broker para pagamento.processado: " + e.getMessage(), e);
        }

        log.info("correlationId={} Evento pagamento.processado publicado {} ({})",
                pagamento.getCorrelationId(), pagamento.getPedidoId(), pagamento.getStatus());
    }
}
