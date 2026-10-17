-- Setor novo (HOLYRICS). O Hibernate criou, a partir do enum Setor, um CHECK (setor IN (...))
-- em cada tabela com coluna "setor", e o ddl-auto: update NUNCA atualiza essas listas: sem esta
-- migração, qualquer INSERT/UPDATE com setor = 'HOLYRICS' quebraria com "violates check constraint".
--
-- Em vez de recriar as listas (e ter que repetir isto a cada setor novo), remove os CHECKs de
-- "setor": quem garante os valores válidos é o próprio enum no Java (@Enumerated(EnumType.STRING)).
-- Os nomes das constraints são gerados pelo Hibernate, então são procurados no catálogo.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT cl.relname AS tabela, c.conname AS restricao
    FROM pg_constraint c
    JOIN pg_class cl ON cl.oid = c.conrelid
    JOIN pg_namespace ns ON ns.oid = cl.relnamespace AND ns.nspname = current_schema()
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.contype = 'c'
      AND a.attname = 'setor'
      AND cl.relname IN ('tb_usuarios', 'tb_usuario_setores_extras', 'tb_slots_escala', 'tb_publicacoes_escala',
                         'tb_cartoes', 'tb_historico_escala', 'tb_ideias')
  LOOP
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.tabela, r.restricao);
    RAISE NOTICE 'Removido CHECK % de %', r.restricao, r.tabela;
  END LOOP;
END $$;
