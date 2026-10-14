package br.org.pascom.repository;

import br.org.pascom.model.SlotEscala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotEscalaRepository extends JpaRepository<SlotEscala, Long> {
    /** Funções antigas (de antes da escala por setor) que ainda não têm setor definido. */
    List<SlotEscala> findBySetorIsNullAndTipoIsNotNull();
}
