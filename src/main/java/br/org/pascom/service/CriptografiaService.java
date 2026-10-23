package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Criptografia simétrica (AES-GCM) usada para guardar segredos no banco (token do Instagram, chave
 * privada das notificações, autorização do Gmail). Nunca guardamos esses segredos em texto puro.
 * <p>
 * <b>Troca da chave</b> (ex.: depois do incidente de segurança do Railway em out/2026): ponha a chave
 * nova em INSTAGRAM_TOKEN_KEY e a antiga em INSTAGRAM_TOKEN_KEY_ANTERIOR. O sistema continua abrindo o
 * que foi gravado com a antiga e, na subida, regrava tudo com a nova ({@link #recriptografar}). Depois
 * do primeiro deploy com as duas, a variável da chave antiga pode ser apagada.
 */
@Service
public class CriptografiaService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_IV_BYTES = 12;

    private final SecretKey chaveAtual;
    private final SecretKey chaveAnterior;

    public CriptografiaService(@Value("${api.security.instagram.chave}") String chave,
                               @Value("${api.security.instagram.chave-anterior:}") String anterior) {
        this.chaveAtual = derivar(chave);
        this.chaveAnterior = anterior == null || anterior.isBlank() || anterior.equals(chave) ? null : derivar(anterior);
    }

    public String encriptar(String textoPuro) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, chaveAtual, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));

            byte[] cifrado = cipher.doFinal(textoPuro.getBytes(StandardCharsets.UTF_8));
            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar segredo.", e);
        }
    }

    public String decriptar(String textoCriptografado) {
        try {
            return abrir(textoCriptografado, chaveAtual);
        } catch (Exception e) {
            if (chaveAnterior != null) {
                try {
                    return abrir(textoCriptografado, chaveAnterior);
                } catch (Exception ignorada) {
                    // cai no erro abaixo
                }
            }
            throw new IllegalStateException("Falha ao decriptar segredo.", e);
        }
    }

    /** Foi gravado com a chave antiga? (então precisa ser regravado com a nova) */
    public boolean gravadoComChaveAnterior(String textoCriptografado) {
        if (chaveAnterior == null || textoCriptografado == null) return false;
        try {
            abrir(textoCriptografado, chaveAtual);
            return false;
        } catch (Exception e) {
            try {
                abrir(textoCriptografado, chaveAnterior);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /** Abre (com a chave que servir) e regrava com a chave atual. */
    public String recriptografar(String textoCriptografado) {
        return encriptar(decriptar(textoCriptografado));
    }

    private static String abrir(String textoCriptografado, SecretKey chave) throws Exception {
        byte[] dados = Base64.getDecoder().decode(textoCriptografado);
        byte[] iv = new byte[TAMANHO_IV_BYTES];
        byte[] cifrado = new byte[dados.length - TAMANHO_IV_BYTES];
        System.arraycopy(dados, 0, iv, 0, iv.length);
        System.arraycopy(dados, iv.length, cifrado, 0, cifrado.length);
        Cipher cipher = Cipher.getInstance(ALGORITMO);
        cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
        return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    /** Deriva uma chave AES-256 estável a partir do segredo configurado (string de qualquer tamanho). */
    private static SecretKey derivar(String segredo) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
