package br.org.pascom.repository;

import br.org.pascom.model.MetricaPostagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricaPostagemRepository extends JpaRepository<MetricaPostagem, Long> {
    List<MetricaPostagem> findByCartaoIdOrderByColetadoEmAsc(Long cartaoId);
}
