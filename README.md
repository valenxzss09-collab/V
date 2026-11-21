# Fofo Raças (Fabric 1.21.11)

Mod de raças estilo Origins. **As raças só precisam estar no servidor** (jogadores entram com o Minecraft normal).
Só `/mods` e `/texturas` exigem que o jogador tenha o mesmo `.jar` no cliente (veja abaixo).
Requer Fabric Loader 0.18.1+, Fabric API e Java 21.

## Como gerar o .jar pelo GitHub
1. Crie um repositório e envie todo o conteúdo desta pasta.
2. Abra a aba **Actions**, clique no workflow **build** (ou em *Run workflow*).
3. Quando terminar, baixe o artefato **fofo-racas** e use o `fofo-racas-1.0.0.jar` na pasta `mods` do servidor.

## Comandos (só OP) — também funcionam como `/raca`
- `/raça mudar <player> <raça>`
- `/raça retirar <player>`
- `/raça selecionar <player>` (abre o menu de novo; o jogador escolhe uma vez)

IDs: kitsune, meio_besta_fera, ulfhednar, selkie, encantados_aquaticos, satiro, dvergar, banshee, lampade, spartoi, mayoirei, val, deusa, o

Quem entra pela primeira vez recebe o menu automaticamente e não consegue usá-lo de novo sem um OP.
As raças ficam salvas em `fofo_racas.json` na pasta do mundo.

## Outros comandos
- `/buff <player> <tipo> <valor>` (OP): ex. `/buff Pessoa forca 20` = +20 de força. Tipos: forca, vida, armadura (valor absoluto) e velocidade, pulo, tamanho (em %). Valor negativo = debuff. Fica salvo até remover.
- `/buff remover <player> [tipo]` e `/buff ver <player>` (OP)
- `/info raças`: lista a raça de cada jogador online. `/info raças <raça>`: vantagens e desvantagens. Qualquer jogador usa.

## Deusa
Só OP concede (`/raça mudar <player> deusa`); não aparece no menu. Ela recebe um **Cajado da Deusa** (graveto).
- Buffs: voa, sem fome, regeneração, +20 HP, +30% velocidade, +50% pulo, +6 de dano, +10 de armadura, resistência, visão noturna, respira na água, imune a fogo e queda.
- **Laser:** toque duplo no Shift (alcance 64 blocos, raio 8 no impacto, recarga de 10 s). Constantes em `RaceEffects.java`.
- **Telecinese:** botão direito com o cajado agarra a criatura na mira (até 30 blocos); de novo arremessa; Shift + botão direito solta.
- A tecla "-" não chega ao servidor, então não dá para usá-la sem um mod no cliente.

## Raça O (só OP: `/raça mudar <player> o`)
Imortal: todo dano é cancelado (inclusive `/kill`); no void ele volta para o alto do mundo.
Recebe o **Cetro do O** (vara de blaze). Botão direito usa o poder atual; **Shift + botão direito troca de poder**:
- **Explosão:** 40 explosões em linha (~120 blocos) e uma final gigante. Recarga 20 s.
- **Congelar:** imobiliza criaturas num raio de 20 e transforma água em gelo. Recarga 10 s.
- **Fogo:** lança-chamas por 2 s que queima alvos e acende o chão. Recarga 5 s.
- **Telecinese:** botão direito agarra / arremessa.
- **Escudo:** 15 s repelindo criaturas e apagando projéteis num raio de 5. Recarga 5 s.
- **Dobrar Água:** jato d'água que empurra alvos e deixa uma poça temporária. Recarga 5 s.
Os valores ficam no começo da seção "Raça O" em `RaceEffects.java`.

## /mods, /texturas e /inv (só OP)
- `/mods` e `/texturas`: resumo de todos os jogadores online. `/mods <player>` e `/texturas <player>`: lista completa.
  O servidor só recebe esses dados de quem tem este `.jar` instalado no cliente; sem ele aparece "sem dados".
  Quando o cliente envia a lista, o jogador recebe um aviso no chat.
- `/inv <player>`: abre o inventário do jogador e **dá para pegar, colocar e trocar itens** (inclusive armadura e mão secundária). A edição é ao vivo. Shift+clique fica desativado.

## HUD de habilidades (cliente)
Quem tem este `.jar` no cliente vê um painel à esquerda da tela com as habilidades da própria raça
(na raça O, mostra os 6 poderes e destaca o selecionado). `/fofohud` liga e desliga. Quem não tem o mod joga normal, sem o painel.

## Tab
A lista de jogadores mostra `Raça - Nome`.

## Se o build falhar
Este projeto foi escrito sem poder compilar. Se o Actions mostrar erro, copie o log e peça o ajuste. Pontos mais prováveis:
- `RaceCommand`: a checagem de permissão mudou na 1.21.11. Alternativa: `.requires(src -> src.hasPermissionLevel(2))`.
- Versões em `gradle.properties`: confira em https://fabricmc.net/develop.
- Nomes de métodos Yarn que mudaram na 1.21.11 (`getEntityWorld`, `setSaturationLevel`, `bondWithPlayer`).

## Observações
- Lampade: tochas com **Shift + botão direito** (mão vazia). O Alt não chega ao servidor; usar Alt exigiria também um mod no cliente.
- Banshee: dobra a duração de poções recebidas; poções arremessadas por ela em outros não são afetadas.
- Mayoirei: lentidão e fraqueza apenas no Nether.
- Raças são enum em `Race.java`; os valores numéricos ficam em `RaceEffects.java`.
