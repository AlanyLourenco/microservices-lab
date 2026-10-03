package br.ufg.lab.estoque.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converte as regras da Etapa 1 nos códigos HTTP e no corpo {"mensagem": ...} do enunciado. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ProdutoInexistenteException.class, ReservaInexistenteException.class})
    public ResponseEntity<ErroResponse> naoEncontrado(RuntimeException e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({EstoqueInsuficienteException.class, ReservaConflitanteException.class})
    public ResponseEntity<ErroResponse> conflito(RuntimeException e) {
        return resposta(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(QuantidadeInvalidaException.class)
    public ResponseEntity<ErroResponse> quantidadeInvalida(QuantidadeInvalidaException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErroResponse> requisicaoMalFormada(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida");
    }

    /** Duas reservas simultâneas com o mesmo correlationId: a segunda viola o UNIQUE e é recusada. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> violacaoDeIntegridade(DataIntegrityViolationException e) {
        return resposta(HttpStatus.CONFLICT, "Reserva concorrente com o mesmo correlationId");
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(mensagem));
    }
}
