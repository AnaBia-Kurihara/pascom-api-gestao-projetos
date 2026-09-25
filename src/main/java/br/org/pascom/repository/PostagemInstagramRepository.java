package br.org.pascom.repository;

import br.org.pascom.model.PostagemInstagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostagemInstagramRepository extends JpaRepository<PostagemInstagram, Long> {
    Optional<PostagemInstagram> findByInstagramMediaId(String instagramMediaId);
    List<PostagemInstagram> findByPublicadoEmBetween(LocalDateTime inicio, LocalDateTime fim);
}
