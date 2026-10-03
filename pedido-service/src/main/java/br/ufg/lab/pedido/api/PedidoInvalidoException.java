package br.ufg.lab.pedido.api;

public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException() {
        super("Informe produtoId e uma quantidade inteira maior que zero");
    }
}
