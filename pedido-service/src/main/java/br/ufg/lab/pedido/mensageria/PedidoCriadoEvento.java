package br.ufg.lab.pedido.mensageria;

/** Evento da Etapa 5, publicado em pedidos.exchange com a routing key pedido.criado. */
public record PedidoCriadoEvento(Long pedidoId, Long produtoId, Integer quantidade, String correlationId) {
}
