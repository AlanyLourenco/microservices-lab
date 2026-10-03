package br.ufg.lab.pagamento.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DecisorPagamentoTest {

    @Test
    void aprovaAproximadamenteOitentaPorCento() {
        DecisorPagamento decisor = new DecisorPagamento(0.8);
        int amostras = 20_000;
        long aprovados = 0;
        for (int i = 0; i < amostras; i++) {
            if (decisor.decidir() == StatusPagamento.APROVADO) {
                aprovados++;
            }
        }
        // desvio-padrão ~0,28 p.p. com 20 mil amostras; margem de 2 p.p. é ~7 desvios
        assertThat((double) aprovados / amostras).isBetween(0.78, 0.82);
    }

    @Test
    void taxaForaDoIntervaloERecusada() {
        assertThatThrownBy(() -> new DecisorPagamento(1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
