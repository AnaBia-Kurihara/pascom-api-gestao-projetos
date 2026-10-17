package br.org.pascom.repository;

import br.org.pascom.model.MontagemFimDeSemana;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MontagemFimDeSemanaRepository extends JpaRepository<MontagemFimDeSemana, Long> {
    Optional<MontagemFimDeSemana> findBySabado(LocalDate sabado);
    Optional<MontagemFimDeSemana> findByTokenMusicos(String token);
    List<MontagemFimDeSemana> findBySabadoBetween(LocalDate inicio, LocalDate fim);
}
