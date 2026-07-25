-- GAP-DH (PR #357): espelha o invariante de domínio `Organization.name` (→ `NonBlankName`) na coluna
-- relacional. O único caminho de leitura de org embrulha `NonBlankName(row[name])`; um seed externo com nome
-- em branco lançaria no decode (pré-existente, hoje coberto só por convenção). Este CHECK fecha a mesma classe
-- do #355 no eixo coluna-relacional. `btrim(name) <> ''` rejeita vazio E whitespace-only — mesma semântica do
-- Kotlin `String.isNotBlank()`. Nenhum registro em branco existe (Organization.init sempre guardou), então a
-- constraint aplica sem falha sobre dados históricos.
ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_name_not_blank CHECK (btrim(name) <> '');
