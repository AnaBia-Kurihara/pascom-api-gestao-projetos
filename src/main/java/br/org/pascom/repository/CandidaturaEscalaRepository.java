package br.org.pascom.repository;

import br.org.pascom.model.CandidaturaEscala;
import br.org.pascom.model.SlotEscala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidaturaEscalaRepository extends JpaRepository<CandidaturaEscala, Long> {
    Optional<CandidaturaEscala> findBySlotIdAndUsuarioId(Long slotId, Long usuarioId);
    List<CandidaturaEscala> findBySlotIdIn(Collection<Long> slotIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from CandidaturaEscala c where c.slot = :slot")
    void apagarDoSlot(@Param("slot") SlotEscala slot);
}
