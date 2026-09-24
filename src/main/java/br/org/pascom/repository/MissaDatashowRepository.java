package br.org.pascom.repository;

import br.org.pascom.model.MissaDatashow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissaDatashowRepository extends JpaRepository<MissaDatashow, Long> {
    List<MissaDatashow> findAllByOrderByDataAscHorarioAsc();
}
