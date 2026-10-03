package br.ufg.lab.pedido.pedido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufg.lab.pedido.api.PedidoInexistenteException;
import br.ufg.lab.pedido.mensageria.OutboxEvento;
import br.ufg.lab.pedido.mensageria.OutboxRepository;
import br.ufg.lab.pedido.mensageria.PedidoCriadoEvento;
import br.ufg.lab.pedido.mensageria.RabbitConfig;

/** Operações que precisam ser atômicas no banco do Pedido Service. */
@Component
public class RegistroPedido {

    private static final Logger log = LoggerFactory.getLogger(RegistroPedido.class);

    private final PedidoRepository pedidos;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public RegistroPedido(PedidoRepository pedidos, OutboxRepository outbox, ObjectMapper objectMapper) {
        this.pedidos = pedidos;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Cria o pedido e registra o evento pedido.criado na outbox, na mesma transação
     * local: ou os dois são gravados, ou nenhum.
     */
    @Transactional
    public Pedido registrarComEvento(Long produtoId, Integer quantidade, String correlationId) {
        Pedido pedido = pedidos.save(new Pedido(produtoId, quantidade, correlationId));
        PedidoCriadoEvento evento = new PedidoCriadoEvento(pedido.getId(), produtoId, quantidade, correlationId);
        outbox.save(new OutboxEvento(pedido.getId(), RabbitConfig.PEDIDOS_EXCHANGE, RabbitConfig.PEDIDO_CRIADO,
                paraJson(evento), correlationId));
        log.info("correlationId={} Evento pedido.criado do pedido {} registrado na outbox", correlationId, pedido.getId());
        return pedido;
    }

    @Transactional
    public Pedido atualizarStatus(Long pedidoId, StatusPedido novoStatus) {
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(PedidoInexistenteException::new);
        pedido.registrarResultadoPagamento(novoStatus);
        return pedido;
    }

    private String paraJson(PedidoCriadoEvento evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o evento " + evento, e);
        }
    }
}
