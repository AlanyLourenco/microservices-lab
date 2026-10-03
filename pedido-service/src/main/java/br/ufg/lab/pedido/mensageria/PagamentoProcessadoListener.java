package br.ufg.lab.pedido.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.ufg.lab.pedido.pedido.PedidoService;

/** Etapa 12: consome o resultado do pagamento e atualiza o status do pedido. */
@Component
public class PagamentoProcessadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagamentoProcessadoListener.class);

    private final PedidoService pedidoService;

    public PagamentoProcessadoListener(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @RabbitListener(queues = RabbitConfig.PAGAMENTO_PROCESSADO)
    public void consumir(PagamentoProcessadoEvento evento) {
        log.info("correlationId={} Evento pagamento.processado recebido: pedido {} status {}",
                evento.correlationId(), evento.pedidoId(), evento.status());
        pedidoService.aplicarResultadoPagamento(evento);
    }
}
