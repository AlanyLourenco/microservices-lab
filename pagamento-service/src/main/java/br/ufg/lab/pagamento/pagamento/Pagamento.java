package br.ufg.lab.pagamento.pagamento;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    private StatusPagamento status;

    @Column(name = "correlation_id")
    private String correlationId;

    private String instancia;

    @Column(name = "processado_em")
    private OffsetDateTime processadoEm;

    protected Pagamento() {
    }

    public Pagamento(Long pedidoId, StatusPagamento status, String correlationId, String instancia) {
        this.pedidoId = pedidoId;
        this.status = status;
        this.correlationId = correlationId;
        this.instancia = instancia;
        this.processadoEm = OffsetDateTime.now();
    }

    public boolean aprovado() {
        return status == StatusPagamento.APROVADO;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getInstancia() {
        return instancia;
    }

    public OffsetDateTime getProcessadoEm() {
        return processadoEm;
    }
}
