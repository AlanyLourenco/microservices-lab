package br.ufg.lab.pedido.mensageria;

/** Evento da Etapa 12, publicado pelo Pagamento Service: status APROVADO ou REJEITADO. */
public record PagamentoProcessadoEvento(Long pedidoId, String status, String correlationId) {
}
