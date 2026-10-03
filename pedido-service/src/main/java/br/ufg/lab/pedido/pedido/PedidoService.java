package br.ufg.lab.pedido.pedido;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.ufg.lab.pedido.api.EstoqueIndisponivelException;
import br.ufg.lab.pedido.api.PedidoInexistenteException;
import br.ufg.lab.pedido.api.PedidoInvalidoException;
import br.ufg.lab.pedido.api.PedidoNaoCriadoException;
import br.ufg.lab.pedido.api.SimulacaoDesabilitadaException;
import br.ufg.lab.pedido.estoque.EstoqueClient;
import br.ufg.lab.pedido.mensageria.PagamentoProcessadoEvento;

/**
 * Orquestra a criação do pedido entre dois serviços com bancos distintos. Como não
 * existe transação única cobrindo Estoque e Pedido, a consistência é obtida com uma
 * Saga orquestrada: cada passo local tem uma ação compensatória (liberar a reserva)
 * executada quando um passo posterior falha.
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidos;
    private final RegistroPedido registro;
    private final EstoqueClient estoque;
    private final boolean simulacaoHabilitada;

    public PedidoService(PedidoRepository pedidos, RegistroPedido registro, EstoqueClient estoque,
                         @Value("${lab.simulacao-falhas.habilitada:false}") boolean simulacaoHabilitada) {
        this.pedidos = pedidos;
        this.registro = registro;
        this.estoque = estoque;
        this.simulacaoHabilitada = simulacaoHabilitada;
    }

    /**
     * Regra da Etapa 3. Se houver estoque: 1) reservar, 2) criar o pedido, 3) publicar o
     * evento. Se não houver: não cria o pedido, não publica o evento e retorna erro.
     * O passo 3 é garantido pela outbox, gravada junto com o pedido no passo 2.
     */
    public Pedido criar(Long produtoId, Integer quantidade, String correlationId, SimulacaoFalha simulacao) {
        if (produtoId == null || quantidade == null || quantidade <= 0) {
            throw new PedidoInvalidoException();
        }
        if (simulacao != SimulacaoFalha.NENHUMA && !simulacaoHabilitada) {
            throw new SimulacaoDesabilitadaException();
        }
        log.info("correlationId={} Requisição de pedido recebida (produto={}, quantidade={})",
                correlationId, produtoId, quantidade);

        try {
            // Sem estoque, o Estoque Service responde 409/404 e a exceção sobe daqui:
            // nenhum pedido é criado e nenhum evento é publicado.
            estoque.reservar(produtoId, quantidade, correlationId);
        } catch (EstoqueIndisponivelException e) {
            // Timeout ou erro de rede: a reserva PODE ter sido feita sem que a resposta
            // chegasse. Como a liberação é idempotente, compensar é sempre seguro.
            compensarReserva(correlationId);
            throw e;
        }

        Pedido pedido;
        try {
            simularFalhaAposReserva(simulacao, correlationId);
            pedido = registro.registrarComEvento(produtoId, quantidade, correlationId);
        } catch (RuntimeException e) {
            log.error("correlationId={} Falha ao criar o pedido depois da reserva do estoque: {}",
                    correlationId, e.getMessage());
            compensarReserva(correlationId);
            throw new PedidoNaoCriadoException();
        }

        log.info("correlationId={} Pedido {} criado", correlationId, pedido.getId());
        return pedido;
    }

    public List<Pedido> listar() {
        return pedidos.findAllByOrderByIdAsc();
    }

    public Pedido consultar(Long id) {
        return pedidos.findById(id).orElseThrow(PedidoInexistenteException::new);
    }

    /**
     * Etapa 12: aplica o resultado publicado pelo Pagamento Service. A operação é
     * idempotente, porque o RabbitMQ entrega "pelo menos uma vez" e o mesmo evento pode
     * chegar duas vezes. Um pedido que já saiu de AGUARDANDO_PAGAMENTO não é alterado.
     */
    public void aplicarResultadoPagamento(PagamentoProcessadoEvento evento) {
        Pedido pedido = pedidos.findById(evento.pedidoId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "Pedido " + evento.pedidoId() + " inexistente; evento enviado para a DLQ"));
        StatusPedido novoStatus = switch (evento.status() == null ? "" : evento.status()) {
            case "APROVADO" -> StatusPedido.PAGO;
            case "REJEITADO" -> StatusPedido.REJEITADO;
            default -> throw new AmqpRejectAndDontRequeueException(
                    "Status de pagamento desconhecido '" + evento.status() + "'; evento enviado para a DLQ");
        };

        if (!pedido.aguardandoPagamento()) {
            log.info("correlationId={} Pedido {} já está {}; evento pagamento.processado repetido ignorado",
                    evento.correlationId(), pedido.getId(), pedido.getStatus());
            return;
        }

        if (novoStatus == StatusPedido.REJEITADO) {
            // Pagamento recusado: o estoque reservado precisa voltar a ficar disponível.
            // Se o Estoque estiver fora, a exceção faz o RabbitMQ reentregar o evento.
            estoque.liberar(pedido.getCorrelationId());
            log.info("correlationId={} Reserva do pedido {} liberada (pagamento rejeitado)",
                    evento.correlationId(), pedido.getId());
        }

        registro.atualizarStatus(pedido.getId(), novoStatus);
        log.info("correlationId={} Pedido {} atualizado para {}", evento.correlationId(), pedido.getId(), novoStatus);
    }

    private void simularFalhaAposReserva(SimulacaoFalha simulacao, String correlationId) {
        switch (simulacao) {
            case ERRO_APOS_RESERVA -> {
                log.warn("correlationId={} [SIMULAÇÃO] Erro após a reserva e antes da criação do pedido", correlationId);
                throw new IllegalStateException("Falha simulada após a reserva do estoque");
            }
            case QUEDA_APOS_RESERVA -> {
                log.error("correlationId={} [SIMULAÇÃO] Queda do processo após a reserva e antes da criação do pedido",
                        correlationId);
                // halt() encerra a JVM imediatamente: nenhum catch, finally ou shutdown hook
                // roda, exatamente como numa queda de energia ou em um kill -9.
                Runtime.getRuntime().halt(1);
            }
            case NENHUMA -> {
            }
        }
    }

    private void compensarReserva(String correlationId) {
        try {
            if (estoque.liberar(correlationId)) {
                log.info("correlationId={} Compensação executada: reserva de estoque desfeita", correlationId);
            } else {
                log.info("correlationId={} Compensação: não havia reserva a desfazer", correlationId);
            }
        } catch (RuntimeException e) {
            log.error("correlationId={} COMPENSAÇÃO FALHOU, a reserva continua pendente no Estoque: {}",
                    correlationId, e.getMessage());
        }
    }
}
