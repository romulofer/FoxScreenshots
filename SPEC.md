# FoxScreenShots — Especificação

> Documento vivo. Fonte da verdade para escopo, arquitetura e limites.
> Mudanças de escopo ou na paleta de cores precisam do aval do autor.

## 1. Objetivo

App de desktop multiplataforma (Windows, Linux, macOS) feito em Flutter para
**capturar** e fazer a **edição básica** de screenshots.

**Público-alvo:** quem usa desktop e precisa de screenshots rápidos e
apresentáveis, com anotação leve — relatos de bug, documentação, tutoriais,
suporte.

**Fora do escopo:** gravação de vídeo/GIF, sincronização em nuvem, gerência de
biblioteca de imagens, edição pesada de fotos (camadas, filtros), OCR. Pode ser
revisto no futuro.

### Modos de operação

1. **Instantâneo (principal)** — o usuário aciona pelo atalho global; todas as
   telas **congelam** (uma foto em resolução plena é mostrada como sobreposição
   de tela cheia, sempre no topo) e o usuário arrasta o retângulo da região a
   recortar.
2. **Temporizador** — o usuário escolhe a região *antes*; a foto é tirada depois
   de um atraso configurável (em segundos). Permite abrir menus, dicas de
   ferramenta, estados de hover etc. antes da captura.

### Execução e acionamento — *decidido*

- Roda na **bandeja do sistema**, em segundo plano (`tray_manager`).
- **Clique esquerdo no ícone da bandeja abre a janela principal** (um hub no
  estilo do Shutter, veja §2.5). Clique direito abre um menu de contexto (os
  dois modos, Configurações, Sair).
- **Atalho global** dispara a captura (`hotkey_manager`), `PrintScreen` por
  padrão, reconfigurável nas Configurações — funciona sem abrir a janela.
- Fechar a janela principal a esconde na bandeja (o app continua rodando); sair
  é explícito (menu da bandeja / menu do app).

## 2. Funcionalidades e critérios de aceite

### 2.1 Captura — *decidido*
- Sobreposição com o quadro congelado em **todos os monitores**; seleção com
  dimensões ao vivo e lupa para acertar a borda pixel a pixel.
- Modo temporizador: escolher a região, escolher o atraso, contagem regressiva,
  captura.
- Backend de captura multiplataforma atrás de um único serviço; nativo por
  sistema operacional (veja §4).

**Aceite:** os dois modos produzem um bitmap recortado correto no Linux
(ambiente de desenvolvimento); Windows e macOS atrás da mesma interface, com
testes de plataforma.

### 2.2 Editor — *decidido (tudo abaixo)*
- **Básico:** recorte, seta, retângulo/elipse, texto.
- **Marca-texto** (marcador translúcido sobre uma região).
- **Desfoque / pixelagem** (tarjar dados sensíveis).
- **Caneta livre** + **marcadores numerados** (1, 2, 3…).
- Desfazer/refazer. Seletor de cor e de espessura. Camada de anotação não
  destrutiva, achatada na exportação.

**Aceite:** cada ferramenta tem teste de unidade do seu modelo/geometria e teste
de widget da sua interação; a exportação compõe as anotações sobre a imagem base
sem perda.

### 2.3 Saída — *decidido*
- **Copiar para a área de transferência** (imagem) **e** salvar em **arquivo**
  (PNG).
- Salvar por diálogo; lembrar a última pasta. Pasta de salvamento automático +
  padrão de nome de arquivo é uma opção das Configurações (com data e hora) —
  desejável, não bloqueante.

**Aceite:** a área de transferência recebe um PNG válido nos três sistemas
operacionais; o arquivo salvo abre em um visualizador comum.

### 2.4 Barra de ferramentas / menus
- Barra de menus do app: **Arquivo**, **Configurações**, mais Editar e Ajuda. O
  menu da bandeja espelha as ações principais.

### 2.5 Janela principal — *hub no estilo do Shutter — decidido*
O clique esquerdo no ícone da bandeja abre um hub inspirado no app **Shutter**:

- **Barra de captura** (topo): Instantâneo (região), Temporizador, Tela cheia,
  Janela ativa. Cada botão inicia o fluxo correspondente.
- **Galeria da sessão** (centro): miniaturas dos screenshots tirados na sessão,
  do mais recente para o mais antigo. Selecionar uma miniatura mostra a prévia.
- **Ações por item:** Editar (abre o editor §2.2), Copiar, Salvar, Excluir,
  Mostrar no gerenciador de arquivos.
- **Rodapé / barra:** Configurações e atalhos rápidos de atraso e de modo.
- A lista da sessão fica em memória, opcionalmente persistida na pasta de saída;
  **não** é uma biblioteca permanente (veja o que está fora do escopo).

**Aceite:** o clique esquerdo na bandeja mostra a janela; uma captura nova
aparece como miniatura; Editar/Copiar/Salvar/Excluir agem sobre o item
selecionado; fechar esconde na bandeja.

### 2.6 Localização — *obrigatório*
- **pt-BR (principal)** e **en-US**, com `flutter_localizations` + arquivos ARB
  do `intl`.
- **pt-BR é o idioma padrão e de fallback** (usado quando o idioma do sistema
  não é nem pt-BR nem en-US, e como ARB base no `template-arb-file`). O idioma
  segue o do sistema quando é um dos suportados, e pode ser trocado nas
  Configurações. Nada de texto fixo no código.

**Aceite:** todo texto visível ao usuário vem do ARB; trocar de idioma atualiza
a interface na hora; os dois idiomas completos (sem chave faltando).

### 2.7 Temas — *obrigatório*
- Esquemas claro/escuro **portados de `~/development/mobile/foxdevelops`**.
- Segue o tema do sistema por padrão, com troca nas Configurações.

Paleta (do foxdevelops `values/colors.xml` + `values-night`):

| Token          | Claro     | Escuro    |
|----------------|-----------|-----------|
| app_background | `#FFFCF9` | `#000000` |
| surface        | `#F2EBE5` | `#241C18` |
| text_primary   | `#1B1411` | `#FFFFFF` |
| text_secondary | `#6A5D56` | `#B8ADA6` |
| brand          | `#A63F10` | `#D9531E` |
| accent         | `#A65A00` | `#FFB74D` |

Observação (vinda do app de origem): o laranja da marca `#D9531E` fica em 3.6:1
sobre branco — abaixo de 4.5:1 —, então a marca no tema **claro** é o `#A63F10`,
mais escuro. Manter assim: não usar `#D9531E` para texto sobre fundo claro.

## 3. Comandos

```bash
flutter pub get                     # instalar dependências
flutter gen-l10n                    # gerar as localizações a partir dos ARB
flutter run -d linux                # rodar (ambiente de desenvolvimento); -d windows / -d macos
flutter analyze                     # análise estática / lints
dart format .                       # formatação
flutter test                        # testes de unidade e de widget
# e2e (precisa de display / xvfb na CI). Um único ponto de entrada: o runner de
# desktop não relança o app para um segundo arquivo na mesma execução.
flutter test integration_test/all_tests.dart -d linux
# regerar as imagens do README (não faz parte da suíte)
xvfb-run -a flutter test integration_test/screenshots_test.dart -d linux
flutter build linux                 # build de release; também windows / macos
```

## 4. Estrutura do projeto

```
lib/
  main.dart                 # bootstrap: window_manager, bandeja, atalhos, roda o app
  app.dart                  # MaterialApp, tema, ligação do idioma
  core/
    theme/                  # app_colors.dart, app_theme.dart (claro/escuro)
    l10n/                   # app_*.arb + gerados
    capture/                # screen_capture_service.dart (interface) + implementações
    hotkey/                 # hotkey_service.dart
    tray/                   # tray_service.dart
    storage/
      settings_service.dart # invólucro do shared_preferences
      output_service.dart   # salvar em arquivo
      clipboard_service.dart# imagem para a área de transferência
    utils/
  features/
    home/                   # janela hub estilo Shutter (§2.5)
      home_screen.dart      # barra de captura + galeria da sessão
      session_controller.dart# lista em memória das capturas da sessão
      widgets/              # thumbnail_tile, capture_toolbar
    capture/
      selection_overlay.dart# sobreposição congelada + seleção elástica
      capture_controller.dart# instantâneo, temporizador, tela cheia, janela ativa
      widgets/              # lupa, etiqueta de dimensões, escurecimento
    editor/
      editor_screen.dart
      models/               # modelos de anotação (imutáveis)
      painters/             # CustomPainter das anotações + compositor
      widgets/              # trilho de ferramentas, barra de estilo, área de desenho
      editor_controller.dart
    settings/
      settings_screen.dart
      settings_controller.dart
    menu/                   # construtores do menu do app e da bandeja
  models/                   # tipos de valor compartilhados (capture_result, region…)
test/                       # unidade + widget
integration_test/           # fluxos e2e + gerador das imagens do README
```

- **Gerência de estado:** Riverpod (`flutter_riverpod`) — testável, modular, sem
  acoplamento a `BuildContext` na lógica.
- **Abstração de captura:** interface `ScreenCaptureService` com implementações
  por sistema operacional escolhidas em tempo de execução; a interface e o
  editor nunca tocam em código de plataforma.

### Dependências principais
`window_manager`, `tray_manager`, `hotkey_manager`, `screen_retriever`,
`flutter_riverpod`, `intl` + `flutter_localizations`, `shared_preferences`,
`image` (operações raster: recorte/codificação), `super_clipboard` (imagem na
área de transferência), `file_selector` + `path_provider`. No Linux, a captura
usa FFI direto com a libX11.

## 5. Estilo de código

- **Effective Dart** + `flutter_lints`; zero aviso do analisador no merge.
- `dart format` (80 colunas, o padrão) obrigatório, com o SDK fixado na CI
  (Flutter 3.47.0 stable / Dart 3.13.0): o estilo do formatador muda entre
  versões do Dart e a CI reprova o que veio de outra.
- Modelos imutáveis; `const` sempre que possível; nada de lógica no `build()`.
- Um tipo principal por arquivo; arquivos em `snake_case.dart`, tipos em
  `UpperCamelCase`.
- Nada de texto fixo (i18n) nem de cor fixa (tokens de tema).
- Comentários de documentação nas APIs públicas dos serviços.
- **Identificadores em inglês** (nomes de classe, método e variável), para não
  destoar das APIs do Flutter. **Documentação do projeto em português do Brasil:**
  README, este documento e as descrições dos testes (`group`, `test`,
  `testWidgets`, `reason:`). Texto visível ao usuário sempre localizado.

## 6. Estratégia de testes

- **Unidade** — serviços (configurações, saída, área de transferência, captura
  por mock), geometria/modelos das ferramentas do editor, compositor de imagem
  (correção de recorte/desfoque/pixelagem).
- **Widget** — interação da sobreposição de seleção, cada ferramenta do editor,
  tela de configurações, troca de idioma e de tema.
- **Golden** — instantâneos das telas principais nos temas claro e escuro.
  *Ainda não implementado;* por enquanto o que existe é o gerador das imagens do
  README (`integration_test/screenshots_test.dart`), que fotografa as telas mas
  não compara com um instantâneo de referência.
- **e2e (`integration_test`)** — fluxos de temporizador e instantâneo com um
  **serviço de captura mockado** (sem depender de display real); teste de fumaça
  da captura real limitado a um runner com display/xvfb.
- **CI:** `flutter analyze` + `flutter test` a cada push; e2e sob xvfb.
- **Revisão de segurança a cada release** (§7).

## 7. Limites

### Sempre
- **Autor único nos commits:** `Rômulo Fernandes Evangelista`
  (`rfe89@hotmail.com`). Nada de linhas `Co-Authored-By` neste repositório.
- Local e offline. Screenshots podem conter dados sensíveis — processar e
  guardar **só na máquina do usuário**.
- Os dois idiomas (pt-BR, en-US) sempre completos. Só tokens de tema, nada de
  cor crua.
- Código modular, reutilizável e bem organizado (conforme os critérios de
  aceite).
- Revisão de segurança antes de cada release.
- **Plataformas de destino:** Windows 10 (64 bits) e 11, macOS e Linux. Windows
  7/8 estão fora — o Flutter suporta Windows 10 ou superior, e o motor usa APIs
  que não existem nas versões antigas.
- O pacote do Windows leva o runtime do Visual C++ (`msvcp140.dll`,
  `vcruntime140.dll`, `vcruntime140_1.dll`) ao lado do `.exe`, copiado pelo
  toolchain da Microsoft na máquina de build (`InstallRequiredSystemLibraries`).
  Origem conhecida e redistribuição app-local prevista na licença — sem esses
  DLLs o app não inicia em máquina sem o redistribuível.

### Perguntar antes
- Adicionar dependência pesada/nativa, ou mudar a abordagem do backend de
  captura.
- Mudar a paleta de cores ou a identidade da marca.
- Qualquer funcionalidade que toque no sistema de arquivos fora da pasta de
  saída escolhida.

### Nunca
- **Nenhuma chamada de rede, telemetria, análise de uso ou relatório de erro que
  mande imagem ou conteúdo de tela para fora da máquina.** Nada de envio
  automático.
- Nenhum segredo no repositório. Nenhum binário de origem desconhecida embutido.
- Não quebrar o funcionamento offline.

---

*Código aberto. Licença: MIT.*
