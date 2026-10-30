package br.org.pascom.repository;

import br.org.pascom.model.InscricaoPush;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface InscricaoPushRepository extends JpaRepository<InscricaoPush, Long> {
    List<InscricaoPush> findByUsuarioIdOrderByCriadaEmAsc(Long usuarioId);

    /** Quem tem pelo menos um aparelho com notificação ativada. */
    @org.springframework.data.jpa.repository.Query("select distinct i.usuarioId from InscricaoPush i")
    List<Long> usuariosComNotificacao();
    Optional<InscricaoPush> findByEndpoint(String endpoint);

    @Transactional
    long deleteByEndpoint(String endpoint);

    @Transactional
    long deleteByEndpointAndUsuarioId(String endpoint, Long usuarioId);
}
