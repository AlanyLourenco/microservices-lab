package br.ufg.lab.pedido.mensageria;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

    public static final String PENDENTE = "PENDENTE";
    public static final String PUBLICADO = "PUBLICADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id")
    private Long pedidoId;

    private String exchange;

    @Column(name = "routing_key")
    private String routingKey;

    private String payload;

    @Column(name = "correlation_id")
    private String correlationId;

    private String status;

    private Integer tentativas;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;

    protected OutboxEvento() {
    }

    public OutboxEvento(Long pedidoId, String exchange, String routingKey, String payload, String correlationId) {
        this.pedidoId = pedidoId;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.status = PENDENTE;
        this.tentativas = 0;
        this.criadoEm = OffsetDateTime.now();
    }

    public void marcarPublicado() {
        this.status = PUBLICADO;
        this.tentativas = tentativas + 1;
        this.publicadoEm = OffsetDateTime.now();
    }

    public void registrarFalha() {
        this.tentativas = tentativas + 1;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getStatus() {
        return status;
    }

    public Integer getTentativas() {
        return tentativas;
    }
}
