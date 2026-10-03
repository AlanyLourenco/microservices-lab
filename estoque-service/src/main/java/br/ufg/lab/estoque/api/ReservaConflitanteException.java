package br.ufg.lab.estoque.api;

public class ReservaConflitanteException extends RuntimeException {

    public ReservaConflitanteException() {
        super("Já existe reserva com este correlationId para outro produto ou quantidade");
    }
}
