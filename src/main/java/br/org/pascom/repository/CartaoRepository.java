package br.org.pascom.repository;

import br.org.pascom.model.Cartao;
import br.org.pascom.model.enums.Etapa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {
    List<Cartao> findByEtapa(Etapa etapa);
    List<Cartao> findByResponsavelId(Long usuarioId);
}
