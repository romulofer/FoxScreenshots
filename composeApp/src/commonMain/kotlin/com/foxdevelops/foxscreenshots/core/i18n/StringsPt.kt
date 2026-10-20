package com.foxdevelops.foxscreenshots.core.i18n

/** Português do Brasil — idioma padrão e de fallback. */
object StringsPt : Strings {
    override val appTitle = "FoxScreenShots"

    override val menuFile = "Arquivo"
    override val menuEdit = "Editar"
    override val menuSettings = "Configurações"
    override val menuHelp = "Ajuda"
    override val showWindow = "Mostrar janela"
    override val quit = "Sair"

    override val captureInstant = "Instantâneo"
    override val captureTimer = "Temporizador"
    override val captureFullScreen = "Tela cheia"
    override val captureActiveWindow = "Janela ativa"
    override val captureRepeat = "Repetir último"

    override val selectionHint = "Arraste para selecionar uma área · Esc cancela"
    override val captureFailedPortalDenied =
        "A captura foi negada pelo sistema. Autorize o FoxScreenShots a capturar a tela e tente de novo."
    override val captureFailedPortalUnavailable =
        "Nenhum portal de captura respondeu. Instale o xdg-desktop-portal da sua área de trabalho."
    override val captureFailedPlatform = "Captura de tela ainda não é suportada neste sistema."
    override val captureFailedDisplay =
        "Não foi possível ler a tela. Verifique se o servidor gráfico está acessível."
    override val captureFailedScreenRecordingDenied =
        "Autorize o FoxScreenShots em Ajustes do Sistema > Privacidade e Segurança > Gravação de Tela e tente de novo."
    override val captureFailedNoActiveWindow = "Nenhuma janela ativa para capturar."
    override val captureFailedWindowNotReady = "A janela do aplicativo ainda não está pronta. Tente de novo."

    override val dependencyTitleBlocking = "A captura de tela está indisponível neste sistema"
    override val dependencyTitleDegraded = "Alguns recursos estão indisponíveis neste sistema"
    override val dependencyX11Library = "libX11.so.6 não encontrada — instale (Debian/Ubuntu: libx11-6)."
    override val dependencyXDisplay = "Servidor gráfico X inacessível — verifique a variável DISPLAY."
    override val dependencyWaylandSession =
        "Sessão Wayland: a captura passa pelo portal do sistema, que pede autorização; " +
            "capturar a janela ativa e o atalho global não funcionam aqui."
    override val dependencyHotkeys =
        "Atalhos globais desligados — não foi possível instalar o gancho de teclado do sistema."
    override val dependencyTray = "O ícone na bandeja não está disponível neste sistema."
    override val dependencyDismiss = "Dispensar"
    override val scrollLockTitle = "Scroll Lock está ativo"
    override val scrollLockMessage =
        "Com a tecla Scroll Lock ligada, os atalhos globais de captura podem não funcionar. " +
            "Desligue-a para voltar a usá-los."

    override val sessionEmpty = "Nenhuma captura ainda. Use a barra acima para começar."
    override val sessionGalleryTitle = "Capturas desta sessão"
    override fun sessionCount(count: Int, max: Int) = "$count/$max"
    override val actionPreview = "Visualizar"
    override val previewTitle = "Pré-visualização"
    override val previewHint = "Role para ampliar · Esc fecha"
    override val actionClose = "Fechar"
    override val actionClearSession = "Limpar sessão"
    override val clearSessionTitle = "Limpar a sessão?"
    override val clearSessionMessage =
        "Todas as capturas desta sessão serão removidas. As já salvas em disco permanecem."
    override val actionClear = "Limpar"

    override val actionEdit = "Editar"
    override val actionCopy = "Copiar"
    override val actionSave = "Salvar"
    override val actionSaveAll = "Salvar tudo"
    override val actionDelete = "Excluir"
    override val actionReveal = "Mostrar na pasta"
    override val actionChange = "Alterar"

    override fun savedCountTo(count: Int, dir: String) =
        if (count == 1) "1 captura salva em $dir" else "$count capturas salvas em $dir"
    override val saveAllFailed = "Não foi possível salvar as capturas"

    override val settingsTitle = "Configurações"
    override val settingsSectionAppearance = "Aparência"
    override val settingsSectionCapture = "Captura"
    override val settingsSectionOutput = "Saída"
    override val settingsTheme = "Tema"
    override val settingsThemeSystem = "Sistema"
    override val settingsThemeLight = "Claro"
    override val settingsThemeDark = "Escuro"
    override val settingsLanguage = "Idioma"
    override val settingsLanguageSystem = "Sistema"
    override val settingsLanguagePt = "Português (Brasil)"
    override val settingsLanguageEn = "Inglês (EUA)"
    override val settingsHotkey = "Atalho de captura"
    override val settingsHotkeyTimer = "Atalho do temporizador"
    override val settingsHotkeyRepeat = "Atalho de repetir último"
    override val settingsCaptureDelay = "Atraso do temporizador (s)"
    override val settingsOutputFolder = "Pasta de saída"
    override val settingsOutputFolderNone = "Nenhuma pasta escolhida"
    override val settingsAutoSave = "Salvar automaticamente"
    override val settingsAutoSaveSubtitle = "Grava cada nova captura na pasta de saída assim que ela é feita."
    override fun settingsVersion(version: String) = "v.$version"

    override val captureTaken = "Captura adicionada à sessão"
    override val copiedToClipboard = "Copiado para a área de transferência"
    override val copyToClipboardFailed = "Não foi possível copiar para a área de transferência"
    override fun savedTo(path: String) = "Salvo em $path"
    override val saveFailed = "Não foi possível salvar o arquivo"

    override val editorTitle = "Editor"
    override val toolCrop = "Recortar"
    override val toolArrow = "Seta"
    override val toolRectangle = "Retângulo"
    override val toolEllipse = "Elipse"
    override val toolText = "Texto"
    override val toolHighlight = "Marca-texto"
    override val toolBlur = "Desfoque"
    override val toolPixelate = "Pixelar"
    override val toolPen = "Caneta"
    override val toolNumber = "Marcador numerado"
    override val undo = "Desfazer"
    override val redo = "Refazer"

    override val editorColor = "Cor"
    override val editorThickness = "Espessura"
    override val editorFill = "Preencher"
    override val editorTextTitle = "Adicionar texto"
    override val editorTextHint = "Digite o texto"
    override val editorApply = "Aplicar na captura"
    override val editorApplied = "Alterações aplicadas"
    override val editorExportFailed = "Não foi possível gerar a imagem editada"
    override val editorDiscardTitle = "Descartar alterações?"
    override val editorDiscardMessage = "As edições feitas nesta captura serão perdidas."
    override val actionDiscard = "Descartar"
    override val actionCancel = "Cancelar"
    override val actionConfirm = "OK"
}
