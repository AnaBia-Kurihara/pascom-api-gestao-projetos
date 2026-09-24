package br.org.pascom.service;

import br.org.pascom.dto.MissaDatashowRequestDTO;
import br.org.pascom.dto.MissaDatashowResponseDTO;
import br.org.pascom.model.MissaDatashow;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.EtapaDatashow;
import br.org.pascom.repository.MissaDatashowRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatashowService {

    private final MissaDatashowRepository missaRepository;
    private final UsuarioRepository usuarioRepository;

    public DatashowService(MissaDatashowRepository missaRepository, UsuarioRepository usuarioRepository) {
        this.missaRepository = missaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<MissaDatashowResponseDTO> listarTodas() {
        return missaRepository.findAllByOrderByDataAscHorarioAsc().stream()
                .map(MissaDatashowResponseDTO::from).toList();
    }

    @Transactional
    public MissaDatashowResponseDTO criar(MissaDatashowRequestDTO dados) {
        MissaDatashow missa = new MissaDatashow();
        missa.setEtapa(EtapaDatashow.AGUARDANDO_MUSICAS);
        preencherCamposEditaveis(missa, dados);
        return MissaDatashowResponseDTO.from(missaRepository.save(missa));
    }

    @Transactional
    public MissaDatashowResponseDTO atualizar(Long id, MissaDatashowRequestDTO dados) {
        MissaDatashow missa = buscarPorId(id);
        preencherCamposEditaveis(missa, dados);
        return MissaDatashowResponseDTO.from(missaRepository.save(missa));
    }

    @Transactional
    public MissaDatashowResponseDTO moverEtapa(Long id, EtapaDatashow novaEtapa) {
        MissaDatashow missa = buscarPorId(id);
        missa.setEtapa(novaEtapa);
        return MissaDatashowResponseDTO.from(missaRepository.save(missa));
    }

    @Transactional
    public void deletar(Long id) {
        missaRepository.delete(buscarPorId(id));
    }

    private MissaDatashow buscarPorId(Long id) {
        return missaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missa não encontrada."));
    }

    private void preencherCamposEditaveis(MissaDatashow missa, MissaDatashowRequestDTO dados) {
        missa.setData(dados.data());
        missa.setHorario(dados.horario());
        missa.setLocal(dados.local() != null && !dados.local().isBlank() ? dados.local() : "Igreja Matriz");
        missa.setListaMusicas(dados.listaMusicas());
        missa.setAvisosOutrasPastorais(dados.avisosOutrasPastorais());
        missa.setResponsavelMontagem(buscarUsuarioOuNulo(dados.responsavelMontagemId()));
        missa.setResponsavelPassar(buscarUsuarioOuNulo(dados.responsavelPassarId()));
    }

    private Usuario buscarUsuarioOuNulo(Long id) {
        if (id == null) return null;
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }
}
