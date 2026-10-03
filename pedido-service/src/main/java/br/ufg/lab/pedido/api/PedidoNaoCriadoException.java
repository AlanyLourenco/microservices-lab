package br.ufg.lab.pedido.api;

public class PedidoNaoCriadoException extends RuntimeException {

    public PedidoNaoCriadoException() {
        super("Falha ao criar o pedido; a reserva de estoque foi desfeita");
    }
}
