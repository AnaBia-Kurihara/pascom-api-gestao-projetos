package br.org.pascom.service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * O "envelope" das notificações no celular (Web Push), feito só com o que já vem no Java — sem
 * biblioteca nova, pra não pesar na memória do Railway:
 * <ul>
 *   <li>criptografia do conteúdo (RFC 8291, "aes128gcm"): só o celular da pessoa consegue ler;</li>
 *   <li>assinatura VAPID (RFC 8292): prova pro Google/Apple/Mozilla que a notificação vem do nosso servidor.</li>
 * </ul>
 * O teste {@code WebPushCriptoTest} confere com o exemplo oficial da RFC 8291.
 */
public final class WebPushCripto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();
    private static final int TAMANHO_REGISTRO = 4096;

    private WebPushCripto() {}

    // ---------------------------------------------------------------- chaves P-256

    public static KeyPair novoParDeChaves() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Chave pública no formato "não comprimido" (65 bytes: 0x04 || X || Y), o que o navegador usa. */
    public static byte[] publicaBruta(ECPublicKey k) {
        byte[] r = new byte[65];
        r[0] = 4;
        copiar32(k.getW().getAffineX(), r, 1);
        copiar32(k.getW().getAffineY(), r, 33);
        return r;
    }

    public static ECPublicKey publicaDeBytes(byte[] bruta) {
        if (bruta.length != 65 || bruta[0] != 4) throw new IllegalArgumentException("Chave pública inválida.");
        try {
            ECPoint p = new ECPoint(new BigInteger(1, Arrays.copyOfRange(bruta, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(bruta, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(p, curva()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Chave pública inválida.", e);
        }
    }

    /** Par de chaves a partir da privada "crua" (32 bytes) e da pública bruta (65 bytes). */
    public static KeyPair parDeBytes(byte[] privada, byte[] publica) {
        try {
            ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, privada), curva()));
            return new KeyPair(publicaDeBytes(publica), priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Chave privada inválida.", e);
        }
    }

    public static byte[] privadaBruta(ECPrivateKey k) {
        byte[] r = new byte[32];
        copiar32(k.getS(), r, 0);
        return r;
    }

    // ---------------------------------------------------------------- conteúdo (RFC 8291)

    /** Criptografa o conteúdo pra uma inscrição (chave p256dh + segredo auth do navegador). */
    public static byte[] criptografar(byte[] conteudo, byte[] p256dh, byte[] auth) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return criptografar(conteudo, p256dh, auth, novoParDeChaves(), salt);
    }

    /** Versão com par efêmero e salt escolhidos (usada no teste com o exemplo da RFC). */
    static byte[] criptografar(byte[] conteudo, byte[] p256dh, byte[] auth, KeyPair efemero, byte[] salt) {
        try {
            byte[] asPublic = publicaBruta((ECPublicKey) efemero.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(efemero.getPrivate());
            ka.doPhase(publicaDeBytes(p256dh), true);
            byte[] segredoEcdh = ka.generateSecret();

            byte[] info = juntar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), p256dh, asPublic);
            byte[] ikm = hkdf(auth, segredoEcdh, info, 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            // Um registro só: conteúdo + 0x02 (delimitador do último registro), sem enchimento.
            byte[] claro = Arrays.copyOf(conteudo, conteudo.length + 1);
            claro[conteudo.length] = 2;
            if (claro.length + 16 > TAMANHO_REGISTRO) throw new IllegalArgumentException("Notificação grande demais.");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cifrado = c.doFinal(claro);

            return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + cifrado.length)
                    .put(salt).putInt(TAMANHO_REGISTRO).put((byte) asPublic.length).put(asPublic).put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- assinatura VAPID (RFC 8292)

    /**
     * Cabeçalho Authorization: "vapid t=&lt;JWT&gt;, k=&lt;chave pública&gt;".
     * @param audiencia origem do serviço de push (ex.: https://fcm.googleapis.com)
     * @param contato   "mailto:" ou URL https de quem envia (a Apple exige que seja válido)
     */
    public static String cabecalhoVapid(String audiencia, String contato, long expiraEmSegundos, KeyPair chaves) {
        try {
            String cab = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String corpo = B64.encodeToString(("{\"aud\":\"" + json(audiencia) + "\",\"exp\":" + expiraEmSegundos
                    + ",\"sub\":\"" + json(contato) + "\"}").getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(chaves.getPrivate());
            s.update((cab + "." + corpo).getBytes(StandardCharsets.US_ASCII));
            String jwt = cab + "." + corpo + "." + B64.encodeToString(s.sign());
            return "vapid t=" + jwt + ", k=" + B64.encodeToString(publicaBruta((ECPublicKey) chaves.getPublic()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- apoio

    public static String b64(byte[] b) { return B64.encodeToString(b); }

    public static byte[] deB64(String s) {
        // o navegador manda em base64url; alguns mandam com "=" no fim ou no alfabeto padrão
        return D64.decode(s.trim().replace('+', '-').replace('/', '_').replaceAll("=+$", ""));
    }

    /** HKDF-SHA256 de um bloco só (basta até 32 bytes). */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int tamanho) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), tamanho);
    }

    private static ECParameterSpec curva() throws GeneralSecurityException {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        return p.getParameterSpec(ECParameterSpec.class);
    }

    private static void copiar32(BigInteger n, byte[] destino, int pos) {
        byte[] b = n.toByteArray();
        int inicio = Math.max(0, b.length - 32), tam = b.length - inicio;
        System.arraycopy(b, inicio, destino, pos + 32 - tam, tam);
    }

    private static byte[] juntar(byte[]... partes) {
        int n = 0;
        for (byte[] p : partes) n += p.length;
        ByteBuffer bb = ByteBuffer.allocate(n);
        for (byte[] p : partes) bb.put(p);
        return bb.array();
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
