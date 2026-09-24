package br.org.pascom.repository;

import br.org.pascom.model.MetricaPostagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricaPostagemRepository extends JpaRepository<MetricaPostagem, Long> {
    List<MetricaPostagem> findByCartaoIdOrderByColetadoEmAsc(Long cartaoId);
    Optional<MetricaPostagem> findFirstByCartaoIdOrderByColetadoEmDesc(Long cartaoId);
}
