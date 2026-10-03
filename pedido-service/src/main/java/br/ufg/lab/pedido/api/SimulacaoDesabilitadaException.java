package br.ufg.lab.pedido.api;

public class SimulacaoDesabilitadaException extends RuntimeException {

    public SimulacaoDesabilitadaException() {
        super("Simulação de falhas desabilitada (LAB_SIMULACAO_FALHAS_HABILITADA=false)");
    }
}
