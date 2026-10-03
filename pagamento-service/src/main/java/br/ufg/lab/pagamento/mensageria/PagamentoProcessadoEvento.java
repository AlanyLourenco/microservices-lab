package br.ufg.lab.pagamento.mensageria;

/** Evento da Etapa 12: {"pedidoId": ..., "status": "APROVADO" | "REJEITADO", "correlationId": ...}. */
public record PagamentoProcessadoEvento(Long pedidoId, String status, String correlationId) {
}
