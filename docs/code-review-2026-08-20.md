# Code review — 2026-08-20

Revisão focada no funcionamento do aplicativo (não estilo), cobrindo três áreas: pipeline de captura de tela, gerenciamento de janela/tray/hotkeys, e editor/armazenamento. Achados ordenados por severidade.

## Críticos

### 1. Wayland: janela do hub trava em fullscreen após a primeira captura

**Arquivo:** `lib/core/window/capture_window_controller.dart:180` (causa), `:219` (onde a flag deveria ser setada), `:324-330` (`_unpin`, onde deveria ser desfeito)

`enterOverlay()` ativa fullscreen nativo via `windowManager.setFullScreen(true)` quando `!_placesWindows` (sessão Wayland, ver `_placesWindows` em `:120`). A flag `_fullScreen`, único gatilho que faz `_unpin()` chamar `setFullScreen(false)`, só é setada dentro do branch X11 de `revealOverlay()` (`_stacking.spanAllMonitors()`, linha 219) — branch que nem executa em Wayland, pois `revealOverlay()` retorna cedo para `!_placesWindows` (linhas 196-199).

**Cenário de falha:** em sessão Wayland, a primeira captura (Instantânea ou Timer) deixa a janela principal presa em fullscreen do SO pelo resto da execução — app fica inutilizável sem reiniciar.

**Status:** corrigido. Duas mudanças: `enterOverlay()` agora seta `_fullScreen = true` no branch Wayland; e `_unpin()` passou a desfazer pelo caminho certo — `_stacking.clear()` no X11 (mensagens client `_NET_WM_STATE`) ou `windowManager.setFullScreen(false)` no Wayland (`NoOverlayStacking.clear()` é no-op, então só setar a flag não bastava).

### 2. Windows: vazamento de handle GDI a cada captura

**Arquivo:** `lib/core/capture/windows_screen_capture_service.dart:148,180`

`win.selectObject(memDc, dib)` descarta o retorno (o bitmap original do DC). O bitmap `dib` segue selecionado em `memDc` quando `win.deleteObject(dib)` é chamado no `finally` (linha 180). Pela semântica do Win32 GDI, `DeleteObject` falha silenciosamente em um bitmap ainda selecionado num DC — o objeto não é liberado.

**Cenário de falha:** toda screenshot no Windows vaza um handle GDI. Uso sustentado (modo timer, capturas repetidas) esgota a cota padrão de objetos GDI por processo (10000), e capturas passam a falhar até reiniciar o app.

**Status:** corrigido. `selectObject` agora guarda o bitmap original do DC; no `finally`, ele é reselecionado em `memDc` antes de `deleteObject(dib)`, seguindo a semântica do Win32 GDI (objeto selecionado num DC não pode ser deletado).

## Alto

### 3. Race entre crop e undo/redo corrompe anotações

**Arquivo:** `lib/features/editor/editor_controller.dart:311-343` (`applyCrop`)

`applyCrop` é assíncrono e só lê `state.annotations` depois do `await` de encode PNG, sem snapshot nem lock. `isBusy` só é checado em `startDraft` — `undo()`, `redo()`, `selectTool()`, `addText()`, `addStep()` não checam, e a UI (`editor_screen.dart`) não observa `isBusy` em lugar nenhum.

**Cenário de falha:** usuário desenha um crop sobre um screenshot já anotado, e clica Undo antes do encode terminar (ainda habilitado). Quando o `await` resolve, `applyCrop` lê as anotações do documento já desfeito, desloca pelo offset do crop errado, e `_commit` descarta o branch de redo sem ação do usuário.

**Status:** corrigido. `applyCrop` agora tira um snapshot de `state.annotations` antes do `await` e desloca esse snapshot, não o estado ao vivo. `undo()`, `redo()`, `selectTool()`, `addText()`, `addStep()` passaram a checar `state.isBusy` e retornar cedo. `editor_screen.dart` também passou a compor `canUndo`/`canRedo` com `!isBusy`, então os botões desabilitam visualmente durante o crop.

### 4. Tray: listener duplicado a cada troca de idioma ou hotkey

**Arquivo:** `lib/core/tray/tray_service.dart:27`, `lib/features/shell/app_shell.dart:57,133`

`TrayService.init()` chama `trayManager.addListener(this)` sem remover um listener anterior. `AppShell._attach()` (que chama `init()`) roda de novo em toda troca de locale (`didChangeDependencies`) e todo rebind de hotkey (`ref.listen`). `ObserverList` do Flutter permite duplicatas.

**Cenário de falha:** após trocar o idioma ou o atalho uma vez, todo clique no ícone da bandeja (Mostrar, Instantânea, Timer, Configurações, Sair) dispara duplicado — inclusive `desktopIntegration.quit()`, chamado duas vezes seguidas.

**Status:** corrigido. `init()` agora chama `trayManager.removeListener(this)` antes de `addListener(this)`, mesmo padrão já usado em `HotkeyService.registerCapture()` (`unregisterAll()` antes de `register()`).

## Médio

### 5. `restore()`/`showWindow()` não forçam foco real de teclado no X11

**Arquivo:** `lib/core/window/capture_window_controller.dart:306-318`, `lib/core/desktop/desktop_integration.dart:74-78`

Só `revealOverlay()` usa o retry (`_ensureFocused`) e `forceFocus()` (`XSetInputFocus` real). `restore()` (pós-captura) e `showWindow()` (clique na bandeja) fazem apenas `windowManager.show()` + `focus()` simples.

**Cenário de falha:** no X11, após uma captura ou ao reabrir a janela pela bandeja, ela reaparece visível mas sem foco real de teclado — usuário precisa clicar manualmente antes de digitar.

**Status:** corrigido. A lógica de retry (`focus()` + `isFocused()`) e `forceFocus()` de `revealOverlay()` virou uma função compartilhada `ensureWindowFocus()` em `window_focus.dart`, usada agora também por `restore()` (`capture_window_controller.dart`) e `showWindow()` (`desktop_integration.dart`, que passou a receber um `WindowFocuser` no construtor).

### 6. macOS: captura multi-monitor com DPI misto recorta errado

**Arquivo:** `lib/core/capture/macos_screen_capture_service.dart:107-146` (`_readLayout`)

A escala é derivada só do display principal e aplicada uniformemente a todos os monitores. Já documentado como limitação conhecida no código-fonte.

**Cenário de falha:** monitor secundário com fator de escala diferente do principal (ex.: Retina + externo não-Retina) tem posição/tamanho de recorte errados.

**Status:** pendente. Corrigir exige capturar por display individualmente (CoreGraphics per-display ou ScreenCaptureKit) — reescrita grande da pipeline macOS, não verificável nesta máquina (sem hardware macOS pra testar). Deixado como está, já documentado no código-fonte como limitação conhecida.

## Baixo

### 7. `ui.Image` vaza se `enterOverlay()` falhar

**Arquivo:** `lib/features/capture/capture_controller.dart:164-169`

`backdrop` é decodificado antes do `try/finally` que o descarta. Se `enterOverlay()` lançar exceção, a imagem decodificada nunca é liberada.

**Status:** corrigido. `backdrop` agora tem seu próprio `try/finally` logo após a decodificação, envolvendo `enterOverlay()` e todo o resto; `mapping` ganhou um `try/finally` interno próprio, já que só existe depois que `enterOverlay()` retorna.

### 8. Cópia para clipboard sem tratamento de erro

**Arquivo:** `lib/features/editor/editor_screen.dart:205-223` (`_onCopy`)

Ao contrário de `_onSave`, não há `try/catch` em volta da chamada ao clipboard. Falha (comum em Wayland) sobe sem exibir o snackbar de erro previsto no código.

**Status:** corrigido. `_onCopy` agora envolve `copyPng` em `try/catch`, tratando exceção como falha (mesmo snackbar `copyToClipboardFailed`).

## Áreas revisadas sem achados relevantes

`session_controller.dart`, `x11_window_geometry.dart`, `x11_overlay_stacking.dart`, `x11_window_focus.dart`, persistência de configurações (`settings_service.dart`/`settings_controller.dart`), transformação de coordenadas do canvas (`canvas_fit.dart`/`editor_compositor.dart`), ciclo de vida de imagens no undo/redo, widgets de UI (`capture_toolbar.dart`, `dependency_banner.dart`, `thumbnail_tile.dart`, `home_screen.dart`).
