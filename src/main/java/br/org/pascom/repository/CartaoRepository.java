package br.org.pascom.repository;

import br.org.pascom.model.Cartao;
import br.org.pascom.model.enums.Setor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {
    List<Cartao> findByExcluidoEmIsNull();
    List<Cartao> findBySetorAndExcluidoEmIsNull(Setor setor);
    List<Cartao> findBySetorAndExcluidoEmIsNotNullOrderByExcluidoEmDesc(Setor setor);
}
