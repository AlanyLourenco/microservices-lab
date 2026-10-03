package br.ufg.lab.pagamento.mensageria;

/**
 * Evento pedido.criado (Etapa 5), na visão do consumidor. É uma cópia do contrato
 * JSON, não uma classe compartilhada com o Pedido Service.
 */
public record PedidoEvento(Long pedidoId, Long produtoId, Integer quantidade, String correlationId) {
}
