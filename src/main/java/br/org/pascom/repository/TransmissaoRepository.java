package br.org.pascom.repository;

import br.org.pascom.model.Transmissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransmissaoRepository extends JpaRepository<Transmissao, Long> {
    List<Transmissao> findAllByOrderByDataAscHorarioAsc();
    boolean existsByDataAndTitulo(LocalDate data, String titulo);
}
