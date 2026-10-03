package br.ufg.lab.estoque.reserva;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCorrelationId(String correlationId);

    /** SELECT ... FOR UPDATE: serializa duas liberações simultâneas da mesma reserva. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.correlationId = :correlationId")
    Optional<Reserva> findByCorrelationIdParaAtualizar(@Param("correlationId") String correlationId);

    List<Reserva> findAllByOrderByIdAsc();

    List<Reserva> findByStatusOrderByIdAsc(StatusReserva status);
}
