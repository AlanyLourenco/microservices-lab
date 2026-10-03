package br.ufg.lab.estoque.reserva;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufg.lab.estoque.api.ReservaInexistenteException;

/**
 * Endpoints que não estão no enunciado: permitem desfazer uma reserva (compensação da
 * Saga) e inspecionar as reservas durante os experimentos de consistência.
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaRepository reservas;
    private final ReservaService service;

    public ReservaController(ReservaRepository reservas, ReservaService service) {
        this.reservas = reservas;
        this.service = service;
    }

    @GetMapping
    public List<Reserva> listar(@RequestParam(required = false) StatusReserva status) {
        return status == null ? reservas.findAllByOrderByIdAsc() : reservas.findByStatusOrderByIdAsc(status);
    }

    @GetMapping("/{correlationId}")
    public Reserva consultar(@PathVariable String correlationId) {
        return reservas.findByCorrelationId(correlationId).orElseThrow(ReservaInexistenteException::new);
    }

    @PutMapping("/{correlationId}/liberar")
    public Reserva liberar(@PathVariable String correlationId) {
        return service.liberar(correlationId);
    }
}
