package br.org.pascom.repository;

import br.org.pascom.model.AlteracaoEscala;
import br.org.pascom.model.enums.Setor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlteracaoEscalaRepository extends JpaRepository<AlteracaoEscala, Long> {
    List<AlteracaoEscala> findTop150BySetorOrderByCriadoEmDesc(Setor setor);
}
