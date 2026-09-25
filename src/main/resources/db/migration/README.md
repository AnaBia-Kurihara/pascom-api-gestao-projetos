# Migrações do Flyway

Só valem pra produção (Postgres, perfil `prod`) — localmente o H2 continua em `ddl-auto:update`,
sem passar por aqui.

Não existe `V1__*.sql`: quando o Flyway foi adotado, o schema já existia (criado ao longo do
tempo pelo `ddl-auto:update` do Hibernate), então a versão 1 foi marcada como "baseline" —
aceita como ponto de partida, sem rodar nada. O próximo arquivo real começa em `V2`.

Pra criar uma migração nova, adicione um arquivo `V{numero}__descricao_curta.sql` (numeração
sempre crescente, nunca reaproveitar um número já usado) com o SQL do Postgres. Exemplos do que
costuma precisar de uma migração aqui, em vez de só esperar o `ddl-auto:update` resolver sozinho:

- Renomear um valor de enum já usado em produção — o `ddl-auto:update` **não** atualiza sozinho
  o `CHECK CONSTRAINT` que o Hibernate gera a partir do enum (isso já causou dor de cabeça antes,
  ver CLAUDE.md). Uma migração aqui com o `ALTER TABLE ... DROP/ADD CONSTRAINT` resolve de vez,
  em vez de precisar lembrar de rodar isso manualmente via `psql` a cada deploy.
- Qualquer mudança que precise popular ou transformar dados já existentes (não só criar uma
  coluna nova em branco, que o `ddl-auto:update` já faz sozinho).

Depois que a próxima migração real for adicionada e confirmada funcionando em produção, vale
considerar trocar `ddl-auto` de `update` pra `validate` no perfil `prod` (`application.yml`) —
aí o Flyway vira o dono de verdade das mudanças de schema, e o Hibernate só confere se bate.
