package br.ufg.lab.estoque.reserva;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufg.lab.estoque.api.EstoqueInsuficienteException;
import br.ufg.lab.estoque.api.ProdutoInexistenteException;
import br.ufg.lab.estoque.api.QuantidadeInvalidaException;
import br.ufg.lab.estoque.api.ReservaConflitanteException;
import br.ufg.lab.estoque.api.ReservaInexistenteException;
import br.ufg.lab.estoque.produto.Produto;
import br.ufg.lab.estoque.produto.ProdutoRepository;

@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

    private final ProdutoRepository produtos;
    private final ReservaRepository reservas;

    public ReservaService(ProdutoRepository produtos, ReservaRepository reservas) {
        this.produtos = produtos;
        this.reservas = reservas;
    }

    /**
     * Regra da Etapa 1: com quantidade suficiente, reduz o disponível. Caso contrário,
     * ou se o produto não existe, o estoque não é alterado. Tudo roda numa única
     * transação local, então uma falha em qualquer passo desfaz o débito e o registro
     * da reserva.
     */
    @Transactional
    public Produto reservar(Long produtoId, Integer quantidade, String correlationId) {
        if (quantidade == null || quantidade <= 0) {
            // Sem esta validação, "quantidade": -5 aumentaria o estoque.
            throw new QuantidadeInvalidaException();
        }
        if (!produtos.existsById(produtoId)) {
            log.warn("correlationId={} Reserva recusada: produto {} inexistente", correlationId, produtoId);
            throw new ProdutoInexistenteException();
        }

        Optional<Reserva> existente = reservas.findByCorrelationId(correlationId);
        if (existente.isPresent()) {
            if (!existente.get().mesmaReserva(produtoId, quantidade)) {
                throw new ReservaConflitanteException();
            }
            log.info("correlationId={} Reserva já registrada para o produto {}; requisição repetida, estoque não alterado",
                    correlationId, produtoId);
            return produtos.findById(produtoId).orElseThrow(ProdutoInexistenteException::new);
        }

        if (produtos.debitar(produtoId, quantidade) == 0) {
            log.warn("correlationId={} Reserva recusada: estoque insuficiente do produto {} (solicitado={})",
                    correlationId, produtoId, quantidade);
            throw new EstoqueInsuficienteException();
        }
        reservas.save(new Reserva(correlationId, produtoId, quantidade));

        Produto produto = produtos.findById(produtoId).orElseThrow(ProdutoInexistenteException::new);
        log.info("correlationId={} Produto {} reservado (quantidade={}, disponivel={})",
                correlationId, produtoId, quantidade, produto.getQuantidade());
        return produto;
    }

    /**
     * Compensação: devolve ao estoque a quantidade de uma reserva. A operação é
     * idempotente, então liberar duas vezes a mesma reserva credita uma única vez.
     * Isso permite ao chamador repeti-la com segurança após timeouts ou reentregas.
     */
    @Transactional
    public Reserva liberar(String correlationId) {
        Reserva reserva = reservas.findByCorrelationIdParaAtualizar(correlationId)
                .orElseThrow(ReservaInexistenteException::new);
        if (reserva.estaLiberada()) {
            log.info("correlationId={} Reserva já estava liberada; nada a fazer", correlationId);
            return reserva;
        }
        // A ordem importa: creditar() limpa o contexto de persistência (clearAutomatically).
        // Marcando a reserva antes, o flush automático grava o novo status antes do UPDATE.
        reserva.liberar();
        produtos.creditar(reserva.getProdutoId(), reserva.getQuantidade());
        log.info("correlationId={} Reserva liberada: produto {} devolvido ao estoque (quantidade={})",
                correlationId, reserva.getProdutoId(), reserva.getQuantidade());
        return reserva;
    }
}
