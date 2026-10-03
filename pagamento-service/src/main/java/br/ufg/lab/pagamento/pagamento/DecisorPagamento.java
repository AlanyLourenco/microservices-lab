package br.ufg.lab.pagamento.pagamento;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Regra da Etapa 6: aprova ou rejeita aleatoriamente, com ~80% de aprovação. */
@Component
public class DecisorPagamento {

    private final double taxaAprovacao;

    public DecisorPagamento(@Value("${pagamento.taxa-aprovacao:0.8}") double taxaAprovacao) {
        if (taxaAprovacao < 0 || taxaAprovacao > 1) {
            throw new IllegalArgumentException("pagamento.taxa-aprovacao deve estar entre 0 e 1");
        }
        this.taxaAprovacao = taxaAprovacao;
    }

    public StatusPagamento decidir() {
        return ThreadLocalRandom.current().nextDouble() < taxaAprovacao
                ? StatusPagamento.APROVADO
                : StatusPagamento.REJEITADO;
    }
}
