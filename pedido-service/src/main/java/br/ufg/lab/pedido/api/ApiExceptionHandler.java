package br.ufg.lab.pedido.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz as falhas do fluxo de criação em respostas HTTP. Os erros vindos do Estoque
 * preservam o código e a mensagem originais (404 / 409), para que o cliente do Pedido
 * Service receba a mesma semântica da Etapa 1.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ProdutoInexistenteException.class, PedidoInexistenteException.class})
    public ResponseEntity<ErroResponse> naoEncontrado(RuntimeException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<ErroResponse> estoqueInsuficiente(EstoqueInsuficienteException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EstoqueIndisponivelException.class)
    public ResponseEntity<ErroResponse> estoqueIndisponivel(EstoqueIndisponivelException e) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(PedidoNaoCriadoException.class)
    public ResponseEntity<ErroResponse> pedidoNaoCriado(PedidoNaoCriadoException e) {
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler({PedidoInvalidoException.class, SimulacaoDesabilitadaException.class})
    public ResponseEntity<ErroResponse> requisicaoInvalida(RuntimeException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErroResponse> requisicaoMalFormada(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida");
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(mensagem));
    }
}
