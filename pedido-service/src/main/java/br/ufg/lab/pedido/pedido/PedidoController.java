package br.ufg.lab.pedido.pedido;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufg.lab.pedido.estoque.EstoqueClient;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    /**
     * Etapa 11: o correlationId nasce aqui, na fronteira do sistema, uma vez por
     * requisição. Ele volta no cabeçalho X-Correlation-Id, inclusive nas respostas de
     * erro, para que o cliente possa informá-lo ao suporte.
     */
    @PostMapping
    public ResponseEntity<Pedido> criar(@RequestBody CriarPedidoRequest request,
                                        @RequestHeader(value = "X-Simular-Falha", defaultValue = "NENHUMA") SimulacaoFalha simulacao,
                                        HttpServletResponse response) {
        String correlationId = UUID.randomUUID().toString();
        response.setHeader(EstoqueClient.CORRELATION_HEADER, correlationId);
        Pedido pedido = service.criar(request.produtoId(), request.quantidade(), correlationId, simulacao);
        return ResponseEntity.created(URI.create("/pedidos/" + pedido.getId())).body(pedido);
    }

    @GetMapping
    public List<Pedido> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Pedido consultar(@PathVariable Long id) {
        return service.consultar(id);
    }

    public record CriarPedidoRequest(Long produtoId, Integer quantidade) {
    }
}
