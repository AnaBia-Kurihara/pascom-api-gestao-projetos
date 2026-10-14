package br.org.pascom.repository;

import br.org.pascom.model.PublicacaoEscala;
import br.org.pascom.model.enums.Setor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicacaoEscalaRepository extends JpaRepository<PublicacaoEscala, Long> {
    Optional<PublicacaoEscala> findByMesAndSetor(String mes, Setor setor);
}
