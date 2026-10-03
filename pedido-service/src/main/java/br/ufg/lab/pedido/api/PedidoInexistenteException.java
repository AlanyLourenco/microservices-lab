package br.ufg.lab.pedido.api;

public class PedidoInexistenteException extends RuntimeException {

    public PedidoInexistenteException() {
        super("Pedido inexistente");
    }
}
