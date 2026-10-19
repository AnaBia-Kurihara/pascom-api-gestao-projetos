package br.org.pascom.service;

import br.org.pascom.model.Aviso;
import br.org.pascom.model.Usuario;
import br.org.pascom.repository.AvisoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Avisos dentro do app (o sininho no topo). Cada aviso também vai como notificação pro celular de quem ativou. */
@Service
public class AvisoService {

    private final AvisoRepository avisoRepository;
    private final PushService pushService;

    public AvisoService(AvisoRepository avisoRepository, PushService pushService) {
        this.avisoRepository = avisoRepository;
        this.pushService = pushService;
    }

    @Transactional
    public void avisar(Usuario usuario, String texto) {
        avisar(usuario, texto, null);
    }

    @Transactional
    public void avisar(Usuario usuario, String texto, String acao) {
        if (usuario == null || texto == null || texto.isBlank()) return;
        String curto = texto.length() > 500 ? texto.substring(0, 497) + "..." : texto;
        avisoRepository.save(Aviso.builder()
                .usuario(usuario)
                .texto(curto)
                .acao(acao)
                .criadoEm(LocalDateTime.now())
                .build());
        pushService.notificar(usuario.getId(), curto, acao);
    }

    /** Já mandou esse aviso (mesma ação) pra pessoa desde aquele instante? Evita repetir. */
    public boolean jaAvisou(Usuario usuario, String acao, LocalDateTime desde) {
        return avisoRepository.existsByUsuarioIdAndAcaoAndCriadoEmGreaterThanEqual(usuario.getId(), acao, desde);
    }

    public Map<String, Object> meus(Usuario usuario) {
        List<Map<String, Object>> lista = avisoRepository.findTop30ByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).stream()
                .map(a -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", a.getId());
                    m.put("texto", a.getTexto());
                    m.put("criadoEm", a.getCriadoEm().toString());
                    m.put("lido", a.getLidoEm() != null);
                    m.put("acao", a.getAcao());
                    return m;
                })
                .toList();
        return Map.of("naoLidos", avisoRepository.countByUsuarioIdAndLidoEmIsNull(usuario.getId()), "avisos", lista);
    }

    @Transactional
    public void marcarLidos(Usuario usuario) {
        avisoRepository.marcarLidos(usuario.getId(), LocalDateTime.now());
    }
}
