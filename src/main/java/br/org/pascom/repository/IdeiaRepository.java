package br.org.pascom.repository;

import br.org.pascom.model.Ideia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeiaRepository extends JpaRepository<Ideia, Long> {
}
