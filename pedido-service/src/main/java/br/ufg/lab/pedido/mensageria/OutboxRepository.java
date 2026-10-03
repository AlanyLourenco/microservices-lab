package br.ufg.lab.pedido.mensageria;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvento, Long> {

    /**
     * Próximo evento pendente, em ordem de criação. FOR UPDATE SKIP LOCKED permite rodar
     * várias instâncias do Pedido Service sem que duas publiquem o mesmo evento: cada
     * instância trava e pula as linhas que outra já está publicando.
     */
    @Query(value = "select * from outbox_evento where status = 'PENDENTE' order by id limit 1 for update skip locked",
            nativeQuery = true)
    Optional<OutboxEvento> travarProximoPendente();
}
