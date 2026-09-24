package br.org.pascom.model.enums;

public enum EtapaTransmissao {
    /** Ainda não tem tema litúrgico do dia definido nem capa/link prontos. */
    PLANEJANDO,
    /** Capa e link já criados, em função da liturgia do dia. */
    CAPA_E_LINK_PRONTOS,
    /** Já foi transmitida. */
    TRANSMITIDO
}
