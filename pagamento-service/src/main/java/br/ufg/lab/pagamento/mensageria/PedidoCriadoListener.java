package br.ufg.lab.pagamento.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufg.lab.pagamento.pagamento.Pagamento;
import br.ufg.lab.pagamento.pagamento.PagamentoService;

/**
 * Consumidor da Etapa 6. Não depende do Pedido Service estar no ar: tudo de que precisa
 * está na mensagem, e o resultado é devolvido também por mensagem (Etapa 12).
 */
@Component
public class PedidoCriadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoListener.class);

    private final PagamentoService pagamentoService;
    private final PagamentoProcessadoPublisher publisher;
    private final long tempoProcessamentoMs;

    public PedidoCriadoListener(PagamentoService pagamentoService, PagamentoProcessadoPublisher publisher,
                                @Value("${pagamento.tempo-processamento-ms:500}") long tempoProcessamentoMs) {
        this.pagamentoService = pagamentoService;
        this.publisher = publisher;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
    }

    @RabbitListener(queues = RabbitConfig.PEDIDO_CRIADO)
    public void consumir(PedidoEvento evento) throws InterruptedException {
        if (evento.pedidoId() == null || evento.correlationId() == null) {
            // Mensagem malformada: tentar de novo não resolve. Vai direto para a DLQ.
            throw new AmqpRejectAndDontRequeueException("Evento pedido.criado inválido: " + evento);
        }
        log.info("correlationId={} Evento pedido.criado recebido: pedido {} (instancia={})",
                evento.correlationId(), evento.pedidoId(), pagamentoService.instancia());

        Thread.sleep(tempoProcessamentoMs); // simula a chamada a um gateway de pagamento

        PagamentoService.Resultado resultado = pagamentoService.processar(evento.pedidoId(), evento.correlationId());
        Pagamento pagamento = resultado.pagamento();
        if (!resultado.novo()) {
            log.info("correlationId={} Pagamento do pedido {} já registrado como {} (evento reentregue); "
                    + "nenhuma nova cobrança, apenas reenvio do resultado",
                    evento.correlationId(), pagamento.getPedidoId(), pagamento.getStatus());
        } else if (pagamento.aprovado()) {
            log.info("correlationId={} Pagamento aprovado {}", evento.correlationId(), pagamento.getPedidoId());
        } else {
            log.info("correlationId={} Pagamento rejeitado {}", evento.correlationId(), pagamento.getPedidoId());
        }

        publisher.publicar(pagamento);
    }
}
