package br.ufg.lab.estoque.api;

public class QuantidadeInvalidaException extends RuntimeException {

    public QuantidadeInvalidaException() {
        super("Quantidade deve ser um inteiro maior que zero");
    }
}
