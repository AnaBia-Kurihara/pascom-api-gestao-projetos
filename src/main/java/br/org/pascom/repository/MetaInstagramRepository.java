package br.org.pascom.repository;

import br.org.pascom.model.MetaInstagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetaInstagramRepository extends JpaRepository<MetaInstagram, Long> {
    List<MetaInstagram> findAllByOrderByCriadoEmDesc();
}
