package br.ufg.lab.pedido.api;

public class EstoqueIndisponivelException extends RuntimeException {

    public EstoqueIndisponivelException() {
        super("Estoque Service indisponível; tente novamente");
    }
}
