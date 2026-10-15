package br.org.pascom.repository;

import br.org.pascom.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {
    List<Aviso> findTop30ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    long countByUsuarioIdAndLidoEmIsNull(Long usuarioId);
    boolean existsByUsuarioIdAndAcaoAndCriadoEmGreaterThanEqual(Long usuarioId, String acao, LocalDateTime desde);

    @Modifying
    @Query("update Aviso a set a.lidoEm = :agora where a.usuario.id = :usuarioId and a.lidoEm is null")
    int marcarLidos(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);
}
