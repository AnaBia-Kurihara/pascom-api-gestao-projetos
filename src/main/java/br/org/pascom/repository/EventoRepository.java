package br.org.pascom.repository;

import br.org.pascom.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    boolean existsByDataAndTitulo(LocalDate data, String titulo);
}