package br.ufg.lab.estoque.reserva;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "produto_id")
    private Long produtoId;

    private Integer quantidade;

    @Enumerated(EnumType.STRING)
    private StatusReserva status;

    @Column(name = "criada_em")
    private OffsetDateTime criadaEm;

    @Column(name = "liberada_em")
    private OffsetDateTime liberadaEm;

    protected Reserva() {
    }

    public Reserva(String correlationId, Long produtoId, Integer quantidade) {
        this.correlationId = correlationId;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.status = StatusReserva.RESERVADA;
        this.criadaEm = OffsetDateTime.now();
    }

    /** Requisição repetida com o mesmo correlationId e os mesmos dados. */
    public boolean mesmaReserva(Long produtoId, Integer quantidade) {
        return this.produtoId.equals(produtoId) && this.quantidade.equals(quantidade);
    }

    public boolean estaLiberada() {
        return status == StatusReserva.LIBERADA;
    }

    public void liberar() {
        this.status = StatusReserva.LIBERADA;
        this.liberadaEm = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    public OffsetDateTime getLiberadaEm() {
        return liberadaEm;
    }
}
