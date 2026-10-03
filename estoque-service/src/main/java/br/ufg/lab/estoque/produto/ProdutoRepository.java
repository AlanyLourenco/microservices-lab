package br.ufg.lab.estoque.produto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findAllByOrderByIdAsc();

    /**
     * Debita o estoque em um único UPDATE condicional. A verificação ("há quantidade
     * suficiente?") e a alteração acontecem atomicamente no banco. Assim, duas reservas
     * concorrentes, mesmo vindas de instâncias diferentes do serviço, nunca deixam o
     * estoque negativo. Ler, comparar em Java e depois salvar abriria uma janela de
     * corrida (lost update).
     *
     * @return 1 se debitou; 0 se a quantidade disponível era insuficiente
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Produto p set p.quantidade = p.quantidade - :quantidade "
            + "where p.id = :id and p.quantidade >= :quantidade")
    int debitar(@Param("id") Long id, @Param("quantidade") int quantidade);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Produto p set p.quantidade = p.quantidade + :quantidade where p.id = :id")
    int creditar(@Param("id") Long id, @Param("quantidade") int quantidade);
}
