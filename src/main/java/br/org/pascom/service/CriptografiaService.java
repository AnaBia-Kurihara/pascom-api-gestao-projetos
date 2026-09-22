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
 * Criptografia simétrica (AES-GCM) usada para guardar segredos de terceiros (ex.: access
 * token do Instagram) no banco. Nunca guardamos esses segredos em texto puro.
 */
@Service
public class CriptografiaService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_IV_BYTES = 12;

    @Value("${api.security.instagram.chave}")
    private String chaveConfigurada;

    public String encriptar(String textoPuro) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));

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
            byte[] dados = Base64.getDecoder().decode(textoCriptografado);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            byte[] cifrado = new byte[dados.length - TAMANHO_IV_BYTES];
            System.arraycopy(dados, 0, iv, 0, iv.length);
            System.arraycopy(dados, iv.length, cifrado, 0, cifrado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decriptar segredo.", e);
        }
    }

    private SecretKey chave() throws Exception {
        // Deriva uma chave AES-256 estável a partir do segredo configurado (string de qualquer tamanho).
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(chaveConfigurada.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }
}
