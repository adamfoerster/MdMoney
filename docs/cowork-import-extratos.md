# Importar extratos bancários para o MdMoney

Instruções do projeto Cowork. Cole isto nas *instruções do projeto*; o texto descreve o formato de
destino por inteiro, então funciona mesmo sem o código do app à mão.

## Regra de ouro

O banco de dados do MdMoney **é o vault Obsidian do usuário** — Markdown que ele também edita à mão.
Não existe "tabela de importação": importar é **escrever notas que o próprio app teria escrito**.
Se uma nota sair fora do formato, o app a lê errado (ou deixa de lê-la) e o usuário perde
visibilidade de dinheiro real.

Três consequências que valem para tudo abaixo:

1. **Nunca sobrescreva uma nota existente.** Leia, mescle, reescreva. Chaves de frontmatter que você
   não entende e qualquer prosa em volta da tabela **são preservadas como estão**.
2. **Nunca invente valor.** Todo número na nota vem de uma linha do extrato; o `total` é sempre a
   soma das linhas da tabela, recalculada, nunca copiada do extrato.
3. **Formato de arquivo ≠ formato de tela.** No arquivo, valor sempre com **ponto** decimal, sem
   zeros à direita (`1232.5`, `92.62`, `200`), **sem símbolo de moeda**, sem separador de milhar.

## 1. Antes de escrever qualquer coisa

- Confirme com o usuário: **qual vault**, **qual conta** (= nome da pasta dentro do vault) e **qual
  arquivo de extrato**.
- Garanta um ponto de retorno: vault versionado em git com árvore limpa, ou uma cópia do diretório.
  Diga qual dos dois você verificou.
- Rode sempre um **dry-run primeiro**: nada é gravado, e você apresenta o plano — linhas lidas,
  linhas ignoradas e por quê, notas que serão criadas, notas que serão mescladas, total por grupo e
  total geral. **Só grave depois de um "pode gravar" explícito.**
- Um extrato contém dado bancário do usuário. Ele fica no vault dele; não o envie para lugar nenhum.

## 2. Ler o extrato

Formato esperado, em xlsx ou csv: `Data | Descrição | Categoria | Valor`. Se o arquivo tiver outras
colunas ou outros nomes, **mostre as cinco primeiras linhas e confirme o mapeamento** antes de seguir.

Normalização:

- **Data** — aceite célula de data ou texto (`m/d/yyyy`, `dd/mm/yyyy`). Se a ordem dia/mês for
  ambígua (`03/04/2026`), pergunte; não chute.
- **Valor** — aceite `1.234,56` e `1,234.56`. Determine o sinal: saída de dinheiro é o caso normal.
- **Descrição** — limpe ruído do banco (`COMPRA CARTAO 1234 -`, códigos de autorização), mas mantenha
  o estabelecimento reconhecível. Essa string vai para a coluna `Note` e é o que o usuário vai ler
  daqui a um ano.
- Linhas de saldo, cabeçalho repetido e totais do extrato **não são transações**: descarte.

## 3. Classificar cada linha antes de escrever

| Tipo de linha | Para onde vai |
| --- | --- |
| Gasto avulso (café, mercado, posto) | linha na **nota de grupo** do mês (§5) |
| Estorno / devolução de um gasto avulso | linha **negativa** na mesma nota de grupo |
| Pagamento de uma conta que já existe como nota mensal (aluguel, internet, escola) | **não crie grupo**: marque o mês como pago na nota existente (§6) |
| Entrada de dinheiro (salário, transferência recebida) | **nunca** vira linha de grupo — vira nota de `type: income` (§7) |
| Transferência entre duas contas do próprio usuário | fora do escopo; liste e pergunte |
| Pagamento da fatura do cartão | **ignore** se as compras do cartão entram por outro extrato — senão o mesmo dinheiro é contado duas vezes |

O item mais fácil de errar é o terceiro. Antes de criar qualquer grupo, **liste as notas de despesa
já existentes na pasta da conta** (as que têm `jan:`…`dec:`) e verifique se a linha do extrato é o
pagamento de uma delas.

Fatura de cartão: o que vale é a **data da compra**, não a do vencimento — é ela que decide em que
mês a linha cai.

## 4. Onde a nota mora

```
<vault>/
  <Conta>.md              nota raiz da conta (saldo inicial, moeda, título)
  <Conta>/                a identidade da conta é o NOME DA PASTA
    2026 Jul - Alimentação.md
    Internet.md
  categories/
    food.md
```

Uma nota de grupo é **uma por conta / ano / mês / grupo**, chamada `<ano> <Mon> - <grupo>.md`, onde
`<Mon>` são as **três primeiras letras do mês em inglês**:
`Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec`. Ex.: `2026 Jul - Alimentação.md`.

Acentos e espaços ficam como estão no nome do arquivo; só é trocado por `-` o que um nome de arquivo
não aceita: `\ / : * ? " < > |`.

## 5. Formato exato da nota de grupo

```markdown
---
title: Alimentação
category: "[[food|Alimentação]]"
account: "[[nubank|Nubank]]"
year: 2026
month: July
total: 26.78
---

| Date     | Note         | Amount |
| -------- | ------------ | ------ |
| 20260715 | Starbucks    | 10.23  |
| 20260715 | Seven Eleven | 5.32   |
| 20260714 | Starbucks    | 11.23  |
```

Regras que não podem escorregar:

- **Ordem das chaves**: `title`, `category`, `account`, `year`, `month`, `total`. Chaves
  desconhecidas que já estejam no arquivo permanecem onde estão.
- `category` e `account` são **wikilinks entre aspas**. As aspas são obrigatórias: o Obsidian lê um
  `[[x]]` solto numa propriedade como lista, não como link. O **alvo** (`food`, `nubank`) é a
  identidade; o texto depois do `|` é só exibição. Se o link já existir apontando para o alvo certo,
  **deixe exatamente como está** — o usuário pode ter ajustado o título à mão.
- Sem categoria conhecida? **Omita a chave** em nota nova (é o que o app faz); se a nota já tiver
  `category:`, deixe-a vazia em vez de apagá-la. Liste essas notas no relatório.
- **Nunca escreva `conta:`.** É o ancestral legado de `account:`: onde já existir, deixe intacto; em
  nota nova, jamais adicione.
- `month` é o **nome do mês em inglês por extenso** (`July`).
- `total` é a **soma das linhas da tabela**, formatada como valor de arquivo.
- **Uma nota de grupo nunca tem `jan:`…`dec:` nem `*-paid:`.** O dinheiro dela mora na tabela. Uma
  nota com chaves de mês é uma conta anual, e não deve receber `month:` em hipótese alguma.
- Exatamente **uma linha em branco** entre o `---` de fechamento e a tabela.

A tabela:

- Colunas fixas `Date | Note | Amount`, nessa ordem.
- `Date` é `yyyyMMdd` (`20260715`) — sem hífens.
- **Mais recente primeiro**; datas iguais mantêm a ordem do extrato.
- `Amount` é **positivo** para gasto (o sinal do extrato não vai para o arquivo) e **negativo** só
  para estorno.
- Cada coluna é preenchida com espaços até a largura da célula mais larga daquela coluna, cabeçalho
  incluído, e a linha separadora tem hifens dessa mesma largura. É o que mantém a tabela legível no
  Obsidian.

## 6. Marcar uma conta mensal como paga

Quando a linha do extrato paga uma nota que já existe (`Internet.md`, `Aluguel.md`), edite **essa**
nota:

- preencha o valor do mês na chave em inglês minúscula (`jul: 129.9`);
- marque `jul-paid: true`;
- não mexa em nenhum outro mês — um valor que você não alterou mantém o texto original;
- se encontrar a chave legada `fev`, deixe-a em paz; migrar não é função da importação.

## 7. Entradas de dinheiro

Entrada nunca é linha de grupo — o total de um grupo é sempre lido como gasto. Uma entrada é uma nota
com `type: income`, valor no mês e `<mon>-paid: true` significando **recebido**:

```markdown
---
title: Salário
category:
account: "[[nubank|Nubank]]"
year: 2026
type: income
jul: 8500
jul-paid: true
---
```

Se já houver uma nota de income do mesmo pagador no ano, preencha o mês nela em vez de criar outra.

## 8. Categorias

Uma categoria é uma nota em `categories/<slug>.md`, e **o nome do arquivo é a identidade**:

```markdown
---
type: category
title: Alimentação
description:
---
```

- O `slug` é o alvo do link (`"[[food|Alimentação]]"`). Use slug curto e minúsculo, e **reaproveite
  os que já existem na pasta** — não crie `comida` se já existe `food`.
- Antes de linkar, garanta que a nota da categoria existe; se não existir, **crie**, para nenhum link
  ficar quebrado.
- Se já existir, **não a edite**: título e descrição são do usuário.
- Mapeie a coluna `Categoria` do extrato para o slug, e mostre esse de-para no relatório para o
  usuário corrigir.

## 9. Mesclar, nunca duplicar

Reimportar o mesmo extrato (ou um que se sobrepõe ao anterior) tem que ser inofensivo:

1. Se a nota já existe, **leia as linhas atuais da tabela**.
2. Uma linha nova é duplicata quando `Date` + `Note` + `Amount` batem com uma existente — pule.
3. Junte existentes + novas, reordene (mais recente primeiro), recalcule o `total`, reescreva o
   arquivo preservando frontmatter desconhecido e prosa.
4. Duas compras iguais, no mesmo dia, no mesmo lugar, pelo mesmo valor **existem** (dois cafés).
   Nesse caso compare a contagem de ocorrências dos dois lados em vez de deduplicar cegamente, e
   sinalize no relatório.

## 10. Proibições

- Não grave vírgula decimal, símbolo de moeda ou separador de milhar dentro de um arquivo.
- Não renomeie pastas de conta, não mova notas, não apague nada.
- Não adicione `month:` a uma nota que tem valores mensais.
- Não escreva no vault real durante teste — use uma cópia.
- Não altere `title:`/`description:` de categorias existentes nem o `conta:` legado.
- Não deduza a conta pelo frontmatter: a identidade é a **pasta**.

## 11. Fechamento

Depois de gravar, releia cada arquivo que tocou e confirme, arquivo por arquivo:

- o frontmatter tem `month:` e **nenhuma** chave `jan:`…`dec:`;
- `total` é exatamente a soma da coluna `Amount`;
- os links têm aspas e apontam para notas que existem;
- nenhum arquivo que você não pretendia tocar aparece como modificado (`git status` no vault, se
  houver git).

Entregue um resumo final: linhas importadas, notas criadas, notas mescladas, duplicatas puladas,
linhas ignoradas com o motivo, categorias criadas e **total por grupo com o total geral** — para o
usuário conferir contra o extrato.

## Anexo: o script que já existe

O repositório do app tem `scripts/import_statement.py`, que faz o caminho xlsx → notas de grupo e já
segue tudo que está aqui: escreve `category:`/`account:` como wikilinks entre aspas, cria a nota da
categoria que faltar, mescla sem duplicar e preserva chaves desconhecidas, prosa e um título de link
ajustado no Obsidian. Use-o quando o extrato estiver nesse formato — mas note que **ele não faz a
triagem do §3**: não sabe reconhecer conta mensal já existente, entrada de dinheiro ou pagamento de
fatura. Essa parte continua sendo sua, antes de entregar as linhas ao script (ou em vez dele).

O de-para de categorias do script fica em `CATEGORY_SLUG`, no topo do arquivo; uma categoria fora do
mapa vira nota sem `category:` e aparece no relatório final.
