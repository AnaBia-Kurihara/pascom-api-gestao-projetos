package br.org.pascom.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Notificações no celular: o envelope tem que sair exatamente como o navegador espera. */
class WebPushCriptoTest {

    /** Exemplo oficial da RFC 8291, seção 5 (as mesmas chaves, salt e conteúdo têm que dar o mesmo resultado). */
    @Test
    void bateComOExemploDaRfc8291() {
        KeyPair as = WebPushCripto.parDeBytes(
                WebPushCripto.deB64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"),
                WebPushCripto.deB64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        byte[] uaPublic = WebPushCripto.deB64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = WebPushCripto.deB64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = WebPushCripto.deB64("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] corpo = WebPushCripto.criptografar("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                uaPublic, auth, as, salt);

        assertThat(WebPushCripto.b64(corpo)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void oCelularConsegueAbrirANotificacao() throws Exception {
        KeyPair celular = WebPushCripto.novoParDeChaves();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        String texto = "🎵 Ministério de teste mandou as músicas da missa de Domingo às 10h.";

        byte[] corpo = WebPushCripto.criptografar(texto.getBytes(StandardCharsets.UTF_8),
                WebPushCripto.publicaBruta((ECPublicKey) celular.getPublic()), auth);

        assertThat(new String(abrir(corpo, celular, auth), StandardCharsets.UTF_8)).isEqualTo(texto);
    }

    @Test
    void assinaturaVapidConfereComAChavePublica() throws Exception {
        KeyPair servidor = WebPushCripto.novoParDeChaves();
        String cab = WebPushCripto.cabecalhoVapid("https://fcm.googleapis.com", "https://pascom.test", 1_900_000_000L, servidor);

        assertThat(cab).startsWith("vapid t=").contains(", k=" + WebPushCripto.b64(WebPushCripto.publicaBruta((ECPublicKey) servidor.getPublic())));
        String jwt = cab.substring("vapid t=".length(), cab.indexOf(", k="));
        String[] partes = jwt.split("\\.");
        assertThat(new String(WebPushCripto.deB64(partes[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,\"sub\":\"https://pascom.test\"}");
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(servidor.getPublic());
        s.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(s.verify(WebPushCripto.deB64(partes[2]))).isTrue();
    }

    @Test
    void chaveGuardadaVoltaIgual() {
        KeyPair par = WebPushCripto.novoParDeChaves();
        KeyPair lido = WebPushCripto.parDeBytes(WebPushCripto.privadaBruta((ECPrivateKey) par.getPrivate()),
                WebPushCripto.publicaBruta((ECPublicKey) par.getPublic()));
        assertThat(lido.getPublic()).isEqualTo(par.getPublic());
        assertThat(((ECPrivateKey) lido.getPrivate()).getS()).isEqualTo(((ECPrivateKey) par.getPrivate()).getS());
    }

    /** O que o navegador faz ao receber (lado de quem lê), pra conferir a ida e a volta. */
    private static byte[] abrir(byte[] corpo, KeyPair celular, byte[] auth) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(corpo);
        byte[] salt = new byte[16];
        bb.get(salt);
        bb.getInt();
        byte[] asPublic = new byte[bb.get()];
        bb.get(asPublic);
        byte[] cifrado = new byte[bb.remaining()];
        bb.get(cifrado);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(celular.getPrivate());
        ka.doPhase(WebPushCripto.publicaDeBytes(asPublic), true);
        byte[] uaPublic = WebPushCripto.publicaBruta((ECPublicKey) celular.getPublic());
        byte[] info = ByteBuffer.allocate(14 + 65 + 65).put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(uaPublic).put(asPublic).array();
        byte[] ikm = hkdf(auth, ka.generateSecret(), info, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] claro = c.doFinal(cifrado);
        assertThat(claro[claro.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(claro, claro.length - 1);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int n) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), n);
    }
}
