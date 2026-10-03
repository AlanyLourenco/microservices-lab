package br.ufg.lab.pedido.pedido;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id")
    private Long produtoId;

    private Integer quantidade;

    /** Enum em vez de String livre: só os três status do enunciado são representáveis. */
    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Pedido() {
    }

    /** Todo novo pedido nasce AGUARDANDO_PAGAMENTO (Etapa 2). */
    public Pedido(Long produtoId, Integer quantidade, String correlationId) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.correlationId = correlationId;
        this.status = StatusPedido.AGUARDANDO_PAGAMENTO;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    public boolean aguardandoPagamento() {
        return status == StatusPedido.AGUARDANDO_PAGAMENTO;
    }

    /**
     * Máquina de estados do pedido: a única transição permitida é
     * AGUARDANDO_PAGAMENTO para PAGO ou para REJEITADO. Um pedido já decidido não
     * muda mais de status.
     */
    public void registrarResultadoPagamento(StatusPedido novoStatus) {
        if (!aguardandoPagamento()) {
            throw new IllegalStateException("Pedido " + id + " já está " + status);
        }
        if (novoStatus == StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new IllegalArgumentException("Resultado de pagamento deve ser PAGO ou REJEITADO");
        }
        this.status = novoStatus;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
