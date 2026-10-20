package br.org.pascom.repository;

import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotEscalaRepository extends JpaRepository<SlotEscala, Long> {
    /** Funções antigas (de antes da escala por setor) que ainda não têm setor definido. */
    List<SlotEscala> findBySetorIsNullAndTipoIsNotNull();

    /**
     * Ocupa a vaga só se ela ainda estiver livre, numa única instrução no banco. Devolve 0 se
     * alguém pegou antes. Evita a corrida de duas pessoas clicando "Eu vou" juntas: com
     * "conferir e depois gravar" as duas passavam na conferência e a segunda apagava a primeira.
     */
    /** Funções de um setor com alguém escalado, em eventos (não excluídos) entre as datas. Relatório de participação. */
    @Query("select s from SlotEscala s join fetch s.evento e join fetch s.voluntario v where s.setor = :setor "
            + "and e.excluidoEm is null and e.data between :inicio and :fim")
    List<SlotEscala> escaladosDoSetorEntre(@Param("setor") br.org.pascom.model.enums.Setor setor,
                                           @Param("inicio") java.time.LocalDate inicio, @Param("fim") java.time.LocalDate fim);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SlotEscala s set s.voluntario = :voluntario where s.id = :id and s.voluntario is null")
    int ocuparSeLivre(@Param("id") Long id, @Param("voluntario") Usuario voluntario);
}
