package br.ufg.lab.pedido.estoque;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import br.ufg.lab.pedido.api.EstoqueIndisponivelException;
import br.ufg.lab.pedido.api.EstoqueInsuficienteException;
import br.ufg.lab.pedido.api.PedidoInvalidoException;
import br.ufg.lab.pedido.api.ProdutoInexistenteException;

/**
 * Única porta de entrada do Pedido Service para o Estoque: a API REST. O Pedido não
 * conhece (nem alcança, pela rede do Compose) o banco do Estoque.
 */
@Component
public class EstoqueClient {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(EstoqueClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EstoqueClient(RestTemplateBuilder builder,
                         @Value("${estoque.service.url}") String baseUrl,
                         @Value("${estoque.service.connect-timeout:2s}") Duration connectTimeout,
                         @Value("${estoque.service.read-timeout:5s}") Duration readTimeout) {
        // Sem timeouts, uma lentidão no Estoque prenderia as threads do Pedido
        // indefinidamente e a falha se propagaria em cascata.
        this.restTemplate = builder.connectTimeout(connectTimeout).readTimeout(readTimeout).build();
        this.baseUrl = baseUrl;
    }

    /** PUT /produtos/{id}/reservar, com o correlationId propagado no cabeçalho. */
    public void reservar(Long produtoId, Integer quantidade, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(CORRELATION_HEADER, correlationId);
        HttpEntity<Map<String, Integer>> entity = new HttpEntity<>(Map.of("quantidade", quantidade), headers);

        try {
            restTemplate.exchange(baseUrl + "/produtos/{id}/reservar", HttpMethod.PUT, entity, Void.class, produtoId);
            log.info("correlationId={} Estoque do produto {} reservado via REST (quantidade={})",
                    correlationId, produtoId, quantidade);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("correlationId={} Estoque respondeu 404: produto {} inexistente", correlationId, produtoId);
            throw new ProdutoInexistenteException();
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("correlationId={} Estoque respondeu 409: estoque insuficiente do produto {}", correlationId, produtoId);
            throw new EstoqueInsuficienteException();
        } catch (HttpClientErrorException.BadRequest e) {
            throw new PedidoInvalidoException();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("correlationId={} Estoque Service indisponível: {}", correlationId, e.getMessage());
            throw new EstoqueIndisponivelException();
        }
    }

    /**
     * PUT /reservas/{correlationId}/liberar: ação compensatória da reserva.
     *
     * @return true se havia reserva e ela foi (ou já estava) liberada; false se nunca existiu
     */
    public boolean liberar(String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CORRELATION_HEADER, correlationId);
        try {
            restTemplate.exchange(baseUrl + "/reservas/{cid}/liberar", HttpMethod.PUT, new HttpEntity<>(headers),
                    Void.class, correlationId);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new EstoqueIndisponivelException();
        }
    }
}
