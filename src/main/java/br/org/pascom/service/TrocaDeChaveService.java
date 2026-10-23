package br.org.pascom.service;

import br.org.pascom.repository.ChavePushRepository;
import br.org.pascom.repository.ContaGmailRepository;
import br.org.pascom.repository.ContaInstagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regrava com a chave atual os segredos que ainda estão com a chave anterior (ver
 * {@link CriptografiaService}): token do Instagram, chave privada das notificações e autorização do
 * Gmail. Roda na subida da aplicação; sem INSTAGRAM_TOKEN_KEY_ANTERIOR não faz nada.
 */
@Service
public class TrocaDeChaveService {

    private final CriptografiaService cripto;
    private final ContaInstagramRepository instagram;
    private final ChavePushRepository push;
    private final ContaGmailRepository gmail;

    public TrocaDeChaveService(CriptografiaService cripto, ContaInstagramRepository instagram,
                               ChavePushRepository push, ContaGmailRepository gmail) {
        this.cripto = cripto;
        this.instagram = instagram;
        this.push = push;
        this.gmail = gmail;
    }

    @Transactional
    public int regravarComChaveNova() {
        int n = 0;
        for (var c : instagram.findAll()) {
            if (cripto.gravadoComChaveAnterior(c.getAccessTokenCriptografado())) {
                c.setAccessTokenCriptografado(cripto.recriptografar(c.getAccessTokenCriptografado()));
                instagram.save(c);
                n++;
            }
        }
        for (var c : push.findAll()) {
            if (cripto.gravadoComChaveAnterior(c.getPrivadaCriptografada())) {
                c.setPrivadaCriptografada(cripto.recriptografar(c.getPrivadaCriptografada()));
                push.save(c);
                n++;
            }
        }
        for (var c : gmail.findAll()) {
            if (cripto.gravadoComChaveAnterior(c.getRefreshTokenCriptografado())) {
                c.setRefreshTokenCriptografado(cripto.recriptografar(c.getRefreshTokenCriptografado()));
                gmail.save(c);
                n++;
            }
        }
        return n;
    }
}
