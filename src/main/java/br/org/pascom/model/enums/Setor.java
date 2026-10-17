package br.org.pascom.model.enums;

/**
 * Setor da pastoral ao qual um {@link br.org.pascom.model.Usuario} pertence.
 * Só é relevante pra quem tem role COORDENADOR ou VOLUNTARIO — o COORDENADOR_GERAL
 * não pertence a um setor específico, coordena todos.
 *
 * Hoje o app inteiro é focado em Redes Sociais; os outros setores existem no cadastro
 * desde já, mas ainda não têm telas próprias (isso é trabalho futuro).
 */
public enum Setor {
    REDES_SOCIAIS,
    JOVENS,
    DATASHOW,
    TRANSMISSOES_VIDEOS,
    /**
     * Holyrics: monta durante a semana o material do Datashow das 4 missas do fim de semana
     * (liturgia, músicas e avisos). Entrou em out/2026 — a migração V2__setor_holyrics.sql tirou os
     * CHECKs de setor do Postgres pra este valor (e os próximos) não quebrarem os INSERTs.
     */
    HOLYRICS
}
