package br.ufg.lab.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.ufg.lab.estoque.produto.ProdutoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // cada teste é desfeito ao final: todos partem dos dados iniciais da Etapa 1
class EstoqueApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProdutoRepository produtos;

    @Test
    void listaOsProdutosIniciais() throws Exception {
        mvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Notebook"))
                .andExpect(jsonPath("$[0].quantidade").value(10))
                .andExpect(jsonPath("$[1].nome").value("Mouse"))
                .andExpect(jsonPath("$[1].quantidade").value(50))
                .andExpect(jsonPath("$[2].nome").value("Teclado"))
                .andExpect(jsonPath("$[2].quantidade").value(20));
    }

    @Test
    void consultaProdutoInexistenteRetorna404() throws Exception {
        mvc.perform(get("/produtos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Produto inexistente"));
    }

    @Test
    void reservaComEstoqueSuficienteReduzQuantidadeERetorna200() throws Exception {
        reservar(1, 2, "cid-1")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "cid-1"))
                .andExpect(jsonPath("$.quantidade").value(8));

        assertThat(produtos.findById(1L).orElseThrow().getQuantidade()).isEqualTo(8);
    }

    @Test
    void reservaMaiorQueODisponivelRetorna409ENaoAlteraEstoque() throws Exception {
        reservar(1, 11, "cid-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Estoque insuficiente"));

        assertThat(produtos.findById(1L).orElseThrow().getQuantidade()).isEqualTo(10);
    }

    @Test
    void reservaDeProdutoInexistenteRetorna404() throws Exception {
        reservar(99, 1, "cid-3")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Produto inexistente"));
    }

    @Test
    void quantidadeNaoPositivaRetorna400ENaoAlteraEstoque() throws Exception {
        reservar(1, -5, "cid-4").andExpect(status().isBadRequest());
        reservar(1, 0, "cid-5").andExpect(status().isBadRequest());

        assertThat(produtos.findById(1L).orElseThrow().getQuantidade()).isEqualTo(10);
    }

    @Test
    void reservaRepetidaComMesmoCorrelationIdNaoDebitaDuasVezes() throws Exception {
        reservar(2, 5, "cid-6").andExpect(status().isOk());
        reservar(2, 5, "cid-6").andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(45));

        assertThat(produtos.findById(2L).orElseThrow().getQuantidade()).isEqualTo(45);
    }

    @Test
    void liberarReservaDevolveEstoqueUmaUnicaVez() throws Exception {
        reservar(3, 4, "cid-7").andExpect(status().isOk());
        assertThat(produtos.findById(3L).orElseThrow().getQuantidade()).isEqualTo(16);

        mvc.perform(put("/reservas/cid-7/liberar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIBERADA"));
        mvc.perform(put("/reservas/cid-7/liberar")).andExpect(status().isOk());

        assertThat(produtos.findById(3L).orElseThrow().getQuantidade()).isEqualTo(20);
    }

    @Test
    void liberarReservaInexistenteRetorna404() throws Exception {
        mvc.perform(put("/reservas/nao-existe/liberar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Reserva inexistente"));
    }

    private org.springframework.test.web.servlet.ResultActions reservar(long produtoId, int quantidade, String cid)
            throws Exception {
        return mvc.perform(put("/produtos/{id}/reservar", produtoId)
                .header("X-Correlation-Id", cid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": " + quantidade + "}"));
    }
}
