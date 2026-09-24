package br.org.pascom.repository;

import br.org.pascom.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    boolean existsByDataAndTituloAndExcluidoEmIsNull(LocalDate data, String titulo);
    List<Evento> findByExcluidoEmIsNull();
    List<Evento> findByExcluidoEmIsNotNullOrderByExcluidoEmDesc();
}