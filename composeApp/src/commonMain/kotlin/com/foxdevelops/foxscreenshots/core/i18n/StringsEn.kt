package com.foxdevelops.foxscreenshots.core.i18n

/** English (US). */
object StringsEn : Strings {
    override val appTitle = "FoxScreenShots"

    override val showWindow = "Show window"
    override val quit = "Quit"

    override val captureInstant = "Instant"
    override val captureTimer = "Timer"
    override val captureFullScreen = "Full screen"
    override val captureActiveWindow = "Active window"
    override val captureRepeat = "Repeat last"

    override val selectionHint = "Drag to select an area · Esc cancels"
    override val captureFailedPortalDenied =
        "The system refused the capture. Allow FoxScreenShots to take screenshots and try again."
    override val captureFailedPortalUnavailable =
        "No screenshot portal answered. Install your desktop's xdg-desktop-portal."
    override val captureFailedPlatform = "Screen capture is not supported on this system yet."
    override val captureFailedDisplay = "Could not read the screen. Check that the display server is reachable."
    override val captureFailedScreenRecordingDenied =
        "Allow FoxScreenShots in System Settings > Privacy & Security > Screen Recording, then try again."
    override val captureFailedNoActiveWindow = "No active window to capture."
    override val captureFailedWindowNotReady = "The app window is not ready yet. Try again."

    override val dependencyTitleBlocking = "Screen capture is unavailable on this system"
    override val dependencyTitleDegraded = "Some features are unavailable on this system"
    override val dependencyX11Library = "libX11.so.6 is missing — install it (Debian/Ubuntu: libx11-6)."
    override val dependencyXDisplay = "The X display server is unreachable — check the DISPLAY variable."
    override val dependencyWaylandSession =
        "Wayland session: capture goes through the system portal, which asks for permission; " +
            "active-window capture and the global hotkey are unavailable here."
    override val dependencyHotkeys =
        "Global hotkeys are off — the system keyboard hook could not be installed. " +
            "Use the window's buttons; restart the app or check the terminal for the reason."
    override val dependencyTray =
        "No tray icon: your desktop does not offer the StatusNotifier protocol (on GNOME, enable the " +
            "AppIndicator extension). Without the tray, closing the window quits the app."
    override val dependencyDismiss = "Dismiss"

    override val sessionEmpty = "No captures yet. Use the toolbar above to start."
    override val sessionGalleryTitle = "Captures this session"
    override fun sessionCount(count: Int, max: Int) = "$count/$max"
    override val actionPreview = "Preview"
    override val previewTitle = "Preview"
    override val previewHint = "Scroll to zoom · Esc closes"
    override val actionClose = "Close"
    override val actionClearSession = "Clear session"
    override val clearSessionTitle = "Clear the session?"
    override val clearSessionMessage =
        "Every capture in this session will be removed. Files already saved to disk stay."
    override val actionClear = "Clear"

    override val actionEdit = "Edit"
    override val actionCopy = "Copy"
    override val actionSave = "Save"
    override val actionSaveAll = "Save all"
    override val actionDelete = "Delete"
    override val actionReveal = "Reveal in folder"
    override val actionChange = "Change"

    override fun savedCountTo(count: Int, dir: String) =
        if (count == 1) "1 capture saved to $dir" else "$count captures saved to $dir"
    override val saveAllFailed = "Could not save the captures"
    override val actionOpenImage = "Open image…"
    override val openImageFailed = "Could not open that image"
    override val quickDelayLabel = "Quick delay"
    override fun delaySeconds(seconds: Int) = "$seconds s"
    override val settingsFileNamePattern = "File name"
    override val settingsFileNamePatternHint = "Use {date}, {time}, {year}, {month}, {day}, {hour}, {minute} and {second}."
    override val aboutTitle = "About FoxScreenShots"
    override val aboutTagline = "Quick screenshot capture and editing."
    override val aboutPrivacy = "Everything runs locally: no network calls, and no screen content ever leaves your machine."
    override val aboutLicense = "Open source under the MIT License."

    override val settingsTitle = "Settings"
    override val settingsSectionAppearance = "Appearance"
    override val settingsSectionCapture = "Capture"
    override val settingsSectionOutput = "Output"
    override val settingsTheme = "Theme"
    override val settingsThemeSystem = "System"
    override val settingsThemeLight = "Light"
    override val settingsThemeDark = "Dark"
    override val settingsLanguage = "Language"
    override val settingsLanguageSystem = "System"
    override val settingsLanguagePt = "Portuguese (Brazil)"
    override val settingsLanguageEn = "English (US)"
    override val settingsHotkey = "Capture hotkey"
    override val settingsHotkeyTimer = "Timer hotkey"
    override val settingsHotkeyRepeat = "Repeat last hotkey"
    override val settingsCaptureDelay = "Timer delay (s)"
    override val settingsOutputFolder = "Output folder"
    override val settingsOutputFolderNone = "No folder chosen"
    override val settingsAutoSave = "Auto-save captures"
    override val settingsAutoSaveSubtitle = "Write every new capture to the output folder as soon as it is taken."
    override fun settingsVersion(version: String) = "v.$version"

    override val captureTaken = "Capture copied and added to the session"
    override val copiedToClipboard = "Copied to clipboard"
    override val copyToClipboardFailed = "Could not copy to the clipboard"
    override fun savedTo(path: String) = "Saved to $path"
    override val saveFailed = "Could not save the file"

    override val editorTitle = "Editor"
    override val toolCrop = "Crop"
    override val toolArrow = "Arrow"
    override val toolRectangle = "Rectangle"
    override val toolEllipse = "Ellipse"
    override val toolText = "Text"
    override val toolHighlight = "Highlight"
    override val toolBlur = "Blur"
    override val toolPixelate = "Pixelate"
    override val toolPen = "Pen"
    override val toolNumber = "Numbered marker"
    override val undo = "Undo"
    override val redo = "Redo"

    override val editorColor = "Color"
    override val editorThickness = "Thickness"
    override val editorFill = "Fill"
    override val editorTextTitle = "Add text"
    override val editorTextHint = "Type the caption"
    override val editorApply = "Apply to capture"
    override val editorApplied = "Changes applied"
    override val editorExportFailed = "Could not render the edited image"
    override val editorDiscardTitle = "Discard changes?"
    override val editorDiscardMessage = "The edits made to this capture will be lost."
    override val actionDiscard = "Discard"
    override val actionCancel = "Cancel"
    override val actionConfirm = "OK"

    override val captureDeleted = "Capture deleted"
    override val captureTakenReplaced = "Capture copied — the oldest one in the session was dropped"
    override val sessionFullHint = "Session full: the next capture drops the oldest one"
    override fun captureMeta(width: Int, height: Int, time: String) = "$width×$height · $time"
    override val revealNeedsSave = "Save the capture to reveal it in its folder"
    override val revealFailed = "Could not open the folder"

    override val timerCancelHint = "Click or press Esc to cancel"
    override val selectionHintAdjust = "Drag the edges to adjust · Enter captures · Esc cancels"
    override val selectionCapture = "Capture"

    override val toolSelect = "Select and move"
    override val editorDeleteAnnotation = "Delete annotation"
    override val editorZoomIn = "Zoom in"
    override val editorZoomOut = "Zoom out"
    override val editorZoomFit = "Fit to window"
    override val editorSize = "Size"
    override val editorUnsaved = "changes not applied"

    override val settingsSelectionConfirm = "Adjust selection before capturing"
    override val settingsSelectionConfirmSubtitle =
        "After dragging, fine-tune the edges and confirm with Enter. Off, the shot is taken when you release the mouse."
    override val settingsHotkeyRecord = "Record…"
    override val hotkeyRecordHint = "Press the combination…"
    override val hotkeyNotAllowed = "Letters need Ctrl, Alt or Meta with them; Esc is reserved"
    override fun hotkeyInUse(by: String) = "That hotkey is already used by “$by”"
    override fun settingsFileNamePreview(example: String) = "Example: $example"
}
