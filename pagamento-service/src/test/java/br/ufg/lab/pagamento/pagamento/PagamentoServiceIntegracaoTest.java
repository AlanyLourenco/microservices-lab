package br.ufg.lab.pagamento.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PagamentoServiceIntegracaoTest {

    @Autowired
    PagamentoService service;

    @Autowired
    PagamentoRepository pagamentos;

    @Test
    void eventoReentregueNaoGeraSegundoPagamentoNemNovoSorteio() {
        PagamentoService.Resultado primeiro = service.processar(500L, "cid-500");
        PagamentoService.Resultado repetido = service.processar(500L, "cid-500");

        assertThat(primeiro.novo()).isTrue();
        assertThat(repetido.novo()).isFalse();
        assertThat(repetido.pagamento().getId()).isEqualTo(primeiro.pagamento().getId());
        assertThat(repetido.pagamento().getStatus()).isEqualTo(primeiro.pagamento().getStatus());
        assertThat(pagamentos.findAll()).filteredOn(p -> p.getPedidoId().equals(500L)).hasSize(1);
    }
}
