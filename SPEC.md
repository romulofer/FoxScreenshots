# FoxScreenShots — Especificação

> Documento vivo. Fonte da verdade para escopo, arquitetura e limites.
> Mudanças de escopo ou na paleta de cores precisam do aval do autor.

## 1. Objetivo

App de desktop multiplataforma (Windows, Linux, macOS) feito em Kotlin
Multiplatform (Compose Multiplatform para desktop) para
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

- Roda na **bandeja do sistema**, em segundo plano (`java.awt.SystemTray`).
- **Clique esquerdo no ícone da bandeja abre a janela principal** (um hub no
  estilo do Shutter, veja §2.5). Clique direito abre um menu de contexto (os
  dois modos, Configurações, Sair).
- **Atalho global** dispara a captura (JNativeHook), `PrintScreen` por
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
  *Implementado:* o diálogo abre na última pasta usada; o padrão de nome aceita
  `{date}`, `{time}`, `{year}`, `{month}`, `{day}`, `{hour}`, `{minute}` e
  `{second}`, e separadores de caminho ou caracteres reservados são descartados.
- **Abrir imagem** (botão na barra do hub): carrega PNG/JPEG/BMP/GIF, converte
  para PNG e abre no editor.

**Aceite:** a área de transferência recebe um PNG válido nos três sistemas
operacionais; o arquivo salvo abre em um visualizador comum.

### 2.4 Barra de ferramentas / menus — *decidido*
- **Sem barra de menus** na janela (decisão do autor). As ações vivem na barra
  superior do hub (abrir imagem, salvar tudo, limpar, configurações, sobre) e
  no menu da bandeja (mostrar, capturas, configurações, sair).
- **Ícone da bandeja:** no Linux, via StatusNotifierItem/D-Bus (Cinnamon, KDE,
  GNOME com AppIndicator), com o AWT como reserva; Windows e macOS usam o AWT.

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
- **pt-BR (principal)** e **en-US**, em duas implementações da interface
  `Strings` (`StringsPt`, `StringsEn`): o compilador recusa um idioma que
  esqueça uma chave.
- **pt-BR é o idioma padrão e de fallback** (usado quando o idioma do sistema
  não é nem pt nem en). O idioma segue o do sistema quando é um dos suportados,
  e pode ser trocado nas Configurações. Nada de texto fixo no código.

**Aceite:** todo texto visível ao usuário vem de `Strings`; trocar de idioma
atualiza a interface na hora; os dois idiomas completos (sem chave faltando).

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

Requer o **JDK 25 ou mais novo**; o Gradle vem pelo wrapper.

```bash
./gradlew :composeApp:run              # rodar o app
./gradlew :composeApp:desktopTest      # testes de unidade e de UI
# testes ao vivo (X11, atalhos globais, área de transferência): só em display
# descartável — injetam teclas e escrevem na área de transferência
FOX_ISOLATED_DISPLAY=1 xvfb-run -a ./gradlew :composeApp:desktopTest
./gradlew :composeApp:packageDeb       # instalador: também packageMsi / packageDmg
./gradlew :composeApp:packageDeb -PappVersion=1.2.3   # versão do pacote (padrão: build.gradle.kts)
```

## 4. Estrutura do projeto

```
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml   # versões num só lugar
composeApp/
  packaging/                  # ícones dos instaladores
  src/
    commonMain/               # tudo que não depende de sistema (Kotlin + Compose)
      model/                  # CaptureRegion, CaptureResult
      core/
        capture/              # ScreenCaptureService (interface), ScreenMapping, falhas
        settings/             # Settings, SettingsController, SettingsStore (interface)
        hotkey/               # HotkeyBinding (parser), GlobalHotkeys (interface)
        i18n/                 # Strings + StringsPt + StringsEn
        theme/                # FoxColors, FoxTheme (claro/escuro)
        storage/              # ClipboardService, OutputService (interfaces)
        desktop/              # DesktopIntegration (bandeja + atalhos), SystemIssues
      features/
        home/                 # hub estilo Shutter: barra de captura + galeria
        capture/              # CaptureController, sobreposição de seleção, contagem
        editor/               # anotações (modelos imutáveis), EditorController, telas
        preview/ settings/
      ui/                     # AppContainer, AppRoot, navegação, mensagens
    desktopMain/              # o que toca no sistema
      app/                    # AppRuntime (liga os serviços reais), janelas de captura
      platform/
        x11/ portal/ windows/ macos/   # backends de captura (JNA / dbus-java)
        image/                # Skia: decodificação, recorte, pintura das anotações
        hotkey/ tray/ storage/ desktop/ session/
    commonTest/ desktopTest/  # unidade + Compose UI + testes ao vivo
```

- **Estado:** `StateFlow` nos controladores (sem acoplamento a `@Composable`
  na lógica); as telas coletam com `collectAsState`.
- **Abstração de captura:** interface `ScreenCaptureService` com uma
  implementação por sistema, escolhida em tempo de execução; a interface e o
  editor nunca tocam em código de plataforma.
- **Janelas de captura:** a sobreposição de seleção e o selo do temporizador
  são **janelas próprias**, nunca a janela principal reaproveitada.

### Dependências principais
Compose Multiplatform (Material 3), `kotlinx-coroutines`, **JNA** (X11,
GDI, CoreGraphics), **dbus-java** (portal do Wayland), **JNativeHook**
(atalhos globais), **FileKit** (diálogos de arquivo nativos), Skia (já vem com
o Compose: imagem, recorte, PNG, anotações). Área de transferência e bandeja
usam o AWT.

## 5. Estilo de código

- **Convenções do Kotlin** (`kotlin.code.style=official`); zero aviso de
  compilação no merge.
- Modelos imutáveis (`data class`, `sealed class`); nada de lógica em
  `@Composable` além de ligar estado a UI.
- Um tipo principal por arquivo; arquivos em `UpperCamelCase.kt`.
- Nada de texto fixo (i18n) nem de cor fixa (tokens de tema).
- Comentários (KDoc) nas APIs públicas dos serviços.
- **Identificadores em inglês** (nomes de classe, método e variável).
  **Documentação do projeto em português do Brasil:** README, este documento e
  as descrições dos testes. Texto visível ao usuário sempre localizado.

## 6. Estratégia de testes

- **Unidade** (`commonTest`) — controladores (configurações, sessão, captura,
  editor), geometria e modelos das ferramentas, mapeamento tela↔imagem,
  atalhos, textos.
- **Imagem** (`desktopTest`) — recorte, desfoque, pixelagem e cada tipo de
  anotação sobre pixels reais (Skia).
- **UI** (`desktopTest`, Compose UI test) — home, editor (cada interação),
  sobreposição de seleção, pré-visualização e configurações, sobre fakes das
  bordas do sistema.
- **Ao vivo** (`desktopTest`, sob `xvfb-run` com `FOX_ISOLATED_DISPLAY=1`) —
  captura real em X11 (tela, região, janela ativa), atalhos globais injetados
  com `xdotool` e a área de transferência. Nunca na sessão do desenvolvedor.
- **CI:** os testes em Linux (xvfb), macOS e Windows a cada push.
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
- **Plataformas de destino:** Windows 10 (64 bits) e 11, macOS e Linux
  (X11; Wayland pelo portal, com limites).
- Os instaladores levam o runtime do Java (jlink): origem conhecida, nada a
  instalar à parte. Windows e macOS saem sem assinatura digital.

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
