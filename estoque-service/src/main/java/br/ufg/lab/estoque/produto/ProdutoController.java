package br.ufg.lab.estoque.produto;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufg.lab.estoque.api.ProdutoInexistenteException;
import br.ufg.lab.estoque.reserva.ReservaService;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final ProdutoRepository produtos;
    private final ReservaService reservas;

    public ProdutoController(ProdutoRepository produtos, ReservaService reservas) {
        this.produtos = produtos;
        this.reservas = reservas;
    }

    @GetMapping
    public List<Produto> listar() {
        return produtos.findAllByOrderByIdAsc();
    }

    @GetMapping("/{id}")
    public Produto consultar(@PathVariable Long id) {
        return produtos.findById(id).orElseThrow(ProdutoInexistenteException::new);
    }

    /**
     * O corpo segue o enunciado ({"quantidade": n}). O correlationId chega no cabeçalho
     * X-Correlation-Id, enviado pelo Pedido Service. Numa chamada manual (cURL/Postman)
     * sem o cabeçalho, o próprio Estoque gera um, para que toda reserva seja rastreável.
     */
    @PutMapping("/{id}/reservar")
    public ResponseEntity<Produto> reservar(@PathVariable Long id,
                                            @RequestBody ReservaRequest request,
                                            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        String cid = (correlationId == null || correlationId.isBlank()) ? UUID.randomUUID().toString() : correlationId;
        Produto produto = reservas.reservar(id, request.quantidade(), cid);
        return ResponseEntity.ok().header(CORRELATION_HEADER, cid).body(produto);
    }

    public record ReservaRequest(Integer quantidade) {
    }
}
