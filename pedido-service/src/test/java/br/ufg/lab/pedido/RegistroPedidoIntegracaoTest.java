package br.ufg.lab.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import br.ufg.lab.pedido.mensageria.OutboxEvento;
import br.ufg.lab.pedido.mensageria.OutboxRepository;
import br.ufg.lab.pedido.pedido.Pedido;
import br.ufg.lab.pedido.pedido.PedidoRepository;
import br.ufg.lab.pedido.pedido.RegistroPedido;
import br.ufg.lab.pedido.pedido.StatusPedido;

/** Sobe o contexto completo (JPA + Flyway + beans de mensageria) sobre H2. */
@SpringBootTest
@ActiveProfiles("test")
class RegistroPedidoIntegracaoTest {

    @Autowired
    RegistroPedido registro;

    @Autowired
    PedidoRepository pedidos;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    TransactionTemplate transacao;

    @Test
    void pedidoEEventoSaoGravadosJuntosEPedidoNasceAguardandoPagamento() {
        Pedido pedido = registro.registrarComEvento(1L, 2, "cid-it");

        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);

        OutboxEvento evento = transacao.execute(s -> outbox.travarProximoPendente().orElseThrow());
        assertThat(evento.getPedidoId()).isEqualTo(pedido.getId());
        assertThat(evento.getExchange()).isEqualTo("pedidos.exchange");
        assertThat(evento.getRoutingKey()).isEqualTo("pedido.criado");
        assertThat(evento.getPayload()).isEqualTo("{\"pedidoId\":" + pedido.getId()
                + ",\"produtoId\":1,\"quantidade\":2,\"correlationId\":\"cid-it\"}");

        Pedido pago = registro.atualizarStatus(pedido.getId(), StatusPedido.PAGO);
        assertThat(pago.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
    }
}
