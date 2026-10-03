package br.ufg.lab.pedido.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.test.util.ReflectionTestUtils;

import br.ufg.lab.pedido.api.EstoqueIndisponivelException;
import br.ufg.lab.pedido.api.EstoqueInsuficienteException;
import br.ufg.lab.pedido.api.PedidoNaoCriadoException;
import br.ufg.lab.pedido.api.ProdutoInexistenteException;
import br.ufg.lab.pedido.api.SimulacaoDesabilitadaException;
import br.ufg.lab.pedido.estoque.EstoqueClient;
import br.ufg.lab.pedido.mensageria.PagamentoProcessadoEvento;

class PedidoServiceTest {

    private static final String CID = "b1f6c1a2-5c5e-4c39-9f52-0d3b0d2c9d10";

    PedidoRepository pedidos;
    RegistroPedido registro;
    EstoqueClient estoque;
    PedidoService service;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepository.class);
        registro = mock(RegistroPedido.class);
        estoque = mock(EstoqueClient.class);
        service = new PedidoService(pedidos, registro, estoque, true);
    }

    @Test
    void comEstoqueReservaECriaPedidoNestaOrdem() {
        when(registro.registrarComEvento(1L, 2, CID)).thenReturn(pedido(10L, StatusPedido.AGUARDANDO_PAGAMENTO));

        Pedido criado = service.criar(1L, 2, CID, SimulacaoFalha.NENHUMA);

        assertThat(criado.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        InOrder ordem = inOrder(estoque, registro);
        ordem.verify(estoque).reservar(1L, 2, CID);
        ordem.verify(registro).registrarComEvento(1L, 2, CID);
    }

    @Test
    void semEstoqueNaoCriaPedidoNemPublicaEvento() {
        doThrow(new EstoqueInsuficienteException()).when(estoque).reservar(1L, 99, CID);

        assertThatThrownBy(() -> service.criar(1L, 99, CID, SimulacaoFalha.NENHUMA))
                .isInstanceOf(EstoqueInsuficienteException.class);
        verify(registro, never()).registrarComEvento(any(), any(), anyString());
    }

    @Test
    void produtoInexistenteNaoCriaPedido() {
        doThrow(new ProdutoInexistenteException()).when(estoque).reservar(99L, 1, CID);

        assertThatThrownBy(() -> service.criar(99L, 1, CID, SimulacaoFalha.NENHUMA))
                .isInstanceOf(ProdutoInexistenteException.class);
        verify(registro, never()).registrarComEvento(any(), any(), anyString());
    }

    @Test
    void falhaAposReservaCompensaLiberandoAReserva() {
        when(estoque.liberar(CID)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(1L, 2, CID, SimulacaoFalha.ERRO_APOS_RESERVA))
                .isInstanceOf(PedidoNaoCriadoException.class);
        verify(estoque).reservar(1L, 2, CID);
        verify(registro, never()).registrarComEvento(any(), any(), anyString());
        verify(estoque).liberar(CID);
    }

    @Test
    void falhaAoGravarPedidoTambemCompensa() {
        when(registro.registrarComEvento(1L, 2, CID)).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatThrownBy(() -> service.criar(1L, 2, CID, SimulacaoFalha.NENHUMA))
                .isInstanceOf(PedidoNaoCriadoException.class);
        verify(estoque).liberar(CID);
    }

    @Test
    void timeoutNoEstoqueTentaCompensarPoisAReservaPodeTerOcorrido() {
        doThrow(new EstoqueIndisponivelException()).when(estoque).reservar(1L, 2, CID);

        assertThatThrownBy(() -> service.criar(1L, 2, CID, SimulacaoFalha.NENHUMA))
                .isInstanceOf(EstoqueIndisponivelException.class);
        verify(estoque).liberar(CID);
        verify(registro, never()).registrarComEvento(any(), any(), anyString());
    }

    @Test
    void simulacaoDesabilitadaERecusada() {
        PedidoService producao = new PedidoService(pedidos, registro, estoque, false);

        assertThatThrownBy(() -> producao.criar(1L, 2, CID, SimulacaoFalha.QUEDA_APOS_RESERVA))
                .isInstanceOf(SimulacaoDesabilitadaException.class);
        verify(estoque, never()).reservar(any(), any(), anyString());
    }

    @Test
    void pagamentoAprovadoMarcaPedidoComoPagoSemMexerNoEstoque() {
        when(pedidos.findById(10L)).thenReturn(Optional.of(pedido(10L, StatusPedido.AGUARDANDO_PAGAMENTO)));

        service.aplicarResultadoPagamento(new PagamentoProcessadoEvento(10L, "APROVADO", CID));

        verify(registro).atualizarStatus(10L, StatusPedido.PAGO);
        verify(estoque, never()).liberar(anyString());
    }

    @Test
    void pagamentoRejeitadoLiberaReservaEMarcaPedidoComoRejeitado() {
        when(pedidos.findById(10L)).thenReturn(Optional.of(pedido(10L, StatusPedido.AGUARDANDO_PAGAMENTO)));

        service.aplicarResultadoPagamento(new PagamentoProcessadoEvento(10L, "REJEITADO", CID));

        InOrder ordem = inOrder(estoque, registro);
        ordem.verify(estoque).liberar(CID);
        ordem.verify(registro).atualizarStatus(10L, StatusPedido.REJEITADO);
    }

    @Test
    void eventoRepetidoNaoAlteraPedidoJaDecidido() {
        when(pedidos.findById(10L)).thenReturn(Optional.of(pedido(10L, StatusPedido.PAGO)));

        service.aplicarResultadoPagamento(new PagamentoProcessadoEvento(10L, "APROVADO", CID));

        verify(registro, never()).atualizarStatus(any(), any());
        verify(estoque, never()).liberar(anyString());
    }

    @Test
    void eventoDePedidoInexistenteVaiParaDlqSemRetentativa() {
        when(pedidos.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarResultadoPagamento(new PagamentoProcessadoEvento(77L, "APROVADO", CID)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void maquinaDeEstadosNaoPermiteMudarPedidoJaDecidido() {
        Pedido pago = pedido(10L, StatusPedido.AGUARDANDO_PAGAMENTO);
        pago.registrarResultadoPagamento(StatusPedido.PAGO);

        assertThatThrownBy(() -> pago.registrarResultadoPagamento(StatusPedido.REJEITADO))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Pedido pedido(Long id, StatusPedido status) {
        Pedido pedido = new Pedido(1L, 2, CID);
        ReflectionTestUtils.setField(pedido, "id", id);
        ReflectionTestUtils.setField(pedido, "status", status);
        return pedido;
    }
}
