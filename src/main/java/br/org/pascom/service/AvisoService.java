package br.org.pascom.service;

import br.org.pascom.model.Aviso;
import br.org.pascom.model.Usuario;
import br.org.pascom.repository.AvisoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Avisos dentro do app (o sininho no topo). */
@Service
public class AvisoService {

    private final AvisoRepository avisoRepository;

    public AvisoService(AvisoRepository avisoRepository) {
        this.avisoRepository = avisoRepository;
    }

    @Transactional
    public void avisar(Usuario usuario, String texto) {
        if (usuario == null || texto == null || texto.isBlank()) return;
        avisoRepository.save(Aviso.builder()
                .usuario(usuario)
                .texto(texto.length() > 500 ? texto.substring(0, 497) + "..." : texto)
                .criadoEm(LocalDateTime.now())
                .build());
    }

    public Map<String, Object> meus(Usuario usuario) {
        List<Map<String, Object>> lista = avisoRepository.findTop30ByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "texto", a.getTexto(),
                        "criadoEm", a.getCriadoEm().toString(),
                        "lido", a.getLidoEm() != null))
                .toList();
        return Map.of("naoLidos", avisoRepository.countByUsuarioIdAndLidoEmIsNull(usuario.getId()), "avisos", lista);
    }

    @Transactional
    public void marcarLidos(Usuario usuario) {
        avisoRepository.marcarLidos(usuario.getId(), LocalDateTime.now());
    }
}
