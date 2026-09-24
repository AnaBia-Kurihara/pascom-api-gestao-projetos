package br.org.pascom.repository;

import br.org.pascom.model.MetricaInstagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetricaInstagramRepository extends JpaRepository<MetricaInstagram, Long> {
    Optional<MetricaInstagram> findFirstByPostagemIdOrderByColetadoEmDesc(Long postagemId);
}
