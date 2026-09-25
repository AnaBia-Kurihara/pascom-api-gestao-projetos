package br.org.pascom.repository;

import br.org.pascom.model.Ideia;
import br.org.pascom.model.enums.Setor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdeiaRepository extends JpaRepository<Ideia, Long> {
    List<Ideia> findByExcluidoEmIsNull();
    List<Ideia> findBySetorAndExcluidoEmIsNull(Setor setor);
    List<Ideia> findBySetorAndExcluidoEmIsNotNullOrderByExcluidoEmDesc(Setor setor);
}
