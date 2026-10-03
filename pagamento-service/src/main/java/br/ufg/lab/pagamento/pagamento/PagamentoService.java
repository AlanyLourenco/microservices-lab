package br.ufg.lab.pagamento.pagamento;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentos;
    private final DecisorPagamento decisor;
    private final String instancia;

    public PagamentoService(PagamentoRepository pagamentos, DecisorPagamento decisor,
                            @Value("${HOSTNAME:local}") String instancia) {
        this.pagamentos = pagamentos;
        this.decisor = decisor;
        this.instancia = instancia;
    }

    /**
     * Registra o pagamento do pedido e persiste o resultado (Etapa 6, passos 1 a 3).
     * É idempotente: se o pedido já tem pagamento (o evento foi reentregue), devolve o
     * registro existente sem sortear de novo. Um pedido nunca recebe dois resultados
     * diferentes.
     */
    @Transactional
    public Resultado processar(Long pedidoId, String correlationId) {
        Optional<Pagamento> existente = pagamentos.findByPedidoId(pedidoId);
        if (existente.isPresent()) {
            return new Resultado(existente.get(), false);
        }
        Pagamento pagamento = pagamentos.save(new Pagamento(pedidoId, decisor.decidir(), correlationId, instancia));
        return new Resultado(pagamento, true);
    }

    public String instancia() {
        return instancia;
    }

    public record Resultado(Pagamento pagamento, boolean novo) {
    }
}
