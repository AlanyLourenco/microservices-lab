package br.ufg.lab.estoque.api;

public class ReservaInexistenteException extends RuntimeException {

    public ReservaInexistenteException() {
        super("Reserva inexistente");
    }
}
