package com.foxdevelops.foxscreenshots.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference

/**
 * Every user-visible string. One implementation per language: the compiler
 * rejects a language that misses a key, so both stay complete.
 */
interface Strings {
    val appTitle: String

    val showWindow: String
    val quit: String

    val captureInstant: String
    val captureTimer: String
    val captureFullScreen: String
    val captureActiveWindow: String
    val captureRepeat: String

    val selectionHint: String
    val captureFailedPortalDenied: String
    val captureFailedPortalUnavailable: String
    val captureFailedPlatform: String
    val captureFailedDisplay: String
    val captureFailedScreenRecordingDenied: String
    val captureFailedNoActiveWindow: String
    val captureFailedWindowNotReady: String

    val dependencyTitleBlocking: String
    val dependencyTitleDegraded: String
    val dependencyX11Library: String
    val dependencyXDisplay: String
    val dependencyWaylandSession: String
    val dependencyHotkeys: String
    val dependencyTray: String
    val dependencyDismiss: String

    val sessionEmpty: String
    val sessionGalleryTitle: String
    fun sessionCount(count: Int, max: Int): String
    val actionPreview: String
    val previewTitle: String
    val previewHint: String
    val actionClose: String
    val actionClearSession: String
    val clearSessionTitle: String
    val clearSessionMessage: String
    val actionClear: String

    val actionEdit: String
    val actionCopy: String
    val actionSave: String
    val actionSaveAll: String
    val actionDelete: String
    val actionReveal: String
    val actionChange: String

    fun savedCountTo(count: Int, dir: String): String
    val saveAllFailed: String

    val settingsTitle: String
    val settingsSectionAppearance: String
    val settingsSectionCapture: String
    val settingsSectionOutput: String
    val settingsTheme: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsLanguage: String
    val settingsLanguageSystem: String
    val settingsLanguagePt: String
    val settingsLanguageEn: String
    val settingsHotkey: String
    val settingsHotkeyTimer: String
    val settingsHotkeyRepeat: String
    val settingsCaptureDelay: String
    val settingsOutputFolder: String
    val settingsOutputFolderNone: String
    val settingsAutoSave: String
    val settingsAutoSaveSubtitle: String
    fun settingsVersion(version: String): String

    val captureTaken: String
    val copiedToClipboard: String
    val copyToClipboardFailed: String
    fun savedTo(path: String): String
    val saveFailed: String

    val editorTitle: String
    val toolCrop: String
    val toolArrow: String
    val toolRectangle: String
    val toolEllipse: String
    val toolText: String
    val toolHighlight: String
    val toolBlur: String
    val toolPixelate: String
    val toolPen: String
    val toolNumber: String
    val undo: String
    val redo: String

    val editorColor: String
    val editorThickness: String
    val editorFill: String
    val editorTextTitle: String
    val editorTextHint: String
    val editorApply: String
    val editorApplied: String
    val editorExportFailed: String
    val editorDiscardTitle: String
    val editorDiscardMessage: String
    val actionDiscard: String
    val actionCancel: String
    val actionConfirm: String

    val captureDeleted: String
    val captureTakenReplaced: String
    val sessionFullHint: String
    fun captureMeta(width: Int, height: Int, time: String): String
    val revealNeedsSave: String
    val revealFailed: String

    val timerCancelHint: String
    val selectionHintAdjust: String
    val selectionCapture: String

    val toolSelect: String
    val editorDeleteAnnotation: String
    val editorZoomIn: String
    val editorZoomOut: String
    val editorZoomFit: String
    val editorSize: String
    val editorUnsaved: String

    val settingsSelectionConfirm: String
    val settingsSelectionConfirmSubtitle: String
    val settingsHotkeyRecord: String
    val hotkeyRecordHint: String
    val hotkeyNotAllowed: String
    fun hotkeyInUse(by: String): String
    fun settingsFileNamePreview(example: String): String

    val actionOpenImage: String
    val openImageFailed: String
    val quickDelayLabel: String
    fun delaySeconds(seconds: Int): String

    val settingsFileNamePattern: String
    val settingsFileNamePatternHint: String

    val aboutTitle: String
    val aboutTagline: String
    val aboutPrivacy: String
    val aboutLicense: String
}

/** Localized explanation of a failed capture. */
fun Strings.captureFailureMessage(failure: CaptureFailure): String = when (failure) {
    CaptureFailure.PortalDenied -> captureFailedPortalDenied
    CaptureFailure.PortalUnavailable -> captureFailedPortalUnavailable
    CaptureFailure.PlatformUnsupported -> captureFailedPlatform
    CaptureFailure.DisplayUnavailable -> captureFailedDisplay
    CaptureFailure.ScreenRecordingDenied -> captureFailedScreenRecordingDenied
    CaptureFailure.NoActiveWindow -> captureFailedNoActiveWindow
    CaptureFailure.WindowNotReady -> captureFailedWindowNotReady
}

/**
 * Picks the strings for a language choice. [systemLanguage] is the OS language
 * code; anything other than `pt`/`en` falls back to pt-BR.
 */
fun stringsFor(preference: LanguagePreference, systemLanguage: String): Strings {
    val code = when (preference) {
        LanguagePreference.Portuguese -> "pt"
        LanguagePreference.English -> "en"
        LanguagePreference.System -> systemLanguage.lowercase()
    }
    return if (code == "en") StringsEn else StringsPt
}

val LocalStrings = staticCompositionLocalOf<Strings> { StringsPt }
