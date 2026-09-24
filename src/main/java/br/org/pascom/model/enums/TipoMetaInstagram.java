package br.org.pascom.model.enums;

public enum TipoMetaInstagram {
    /** Média de curtidas por post, considerando os posts dos últimos 30 dias. */
    CURTIDAS_MEDIA,
    /** Soma do alcance de todos os posts publicados no mês corrente. */
    ALCANCE_MENSAL,
    /** Quantidade de posts publicados nos últimos 7 dias. */
    POSTS_POR_SEMANA,
    /** Média de curtidas+comentários+salvamentos+compartilhamentos por post (últimos 30 dias). */
    ENGAJAMENTO_MEDIO
}
