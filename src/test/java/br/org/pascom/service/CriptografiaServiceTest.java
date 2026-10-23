package br.org.pascom.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Troca da chave de criptografia sem perder o que já estava guardado no banco. */
class CriptografiaServiceTest {

    @Test
    void chaveNovaAbreOQueFoiGravadoComAAntigaERegrava() {
        String gravado = new CriptografiaService("chave-antiga", "").encriptar("token-secreto");

        CriptografiaService nova = new CriptografiaService("chave-nova", "chave-antiga");
        assertThat(nova.decriptar(gravado)).isEqualTo("token-secreto");
        assertThat(nova.gravadoComChaveAnterior(gravado)).isTrue();

        String regravado = nova.recriptografar(gravado);
        assertThat(nova.gravadoComChaveAnterior(regravado)).isFalse();
        // Depois de regravar, a chave antiga já não é necessária.
        assertThat(new CriptografiaService("chave-nova", "").decriptar(regravado)).isEqualTo("token-secreto");
    }

    @Test
    void semAChaveAnteriorOAntigoNaoAbre() {
        String gravado = new CriptografiaService("chave-antiga", "").encriptar("token-secreto");
        assertThatThrownBy(() -> new CriptografiaService("chave-nova", "").decriptar(gravado))
                .isInstanceOf(IllegalStateException.class);
    }
}
