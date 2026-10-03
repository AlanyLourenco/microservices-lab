package br.ufg.lab.pedido.pedido;

/**
 * Falhas injetáveis pelo cabeçalho X-Simular-Falha, para o experimento de consistência
 * da Etapa 3 ("falha após a reserva do estoque e antes da criação do pedido").
 */
public enum SimulacaoFalha {

    /** Nenhuma falha: fluxo normal. */
    NENHUMA,

    /**
     * Exceção depois da reserva. O processo continua vivo, então o Pedido Service
     * consegue executar a compensação (liberar a reserva).
     */
    ERRO_APOS_RESERVA,

    /**
     * O processo morre (Runtime.halt) depois da reserva. Nenhum catch/finally roda,
     * então a compensação NÃO acontece. É o cenário que mostra por que não existe
     * transação única entre os dois serviços.
     */
    QUEDA_APOS_RESERVA
}
