package com.foxdevelops.foxscreenshots.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Made-up screens to photograph: the README images must not contain anybody's real desktop, so every
 * "capture" in them is drawn here.
 */
object SyntheticShots {
    private val ink = Color(0x1B1411)

    private fun canvas(width: Int, height: Int, draw: Graphics2D.() -> Unit): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.draw()
        g.dispose()
        return image
    }

    private fun Graphics2D.bar(x: Int, y: Int, w: Int, h: Int, color: Color, arc: Int = 6) {
        this.color = color
        fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), arc.toFloat(), arc.toFloat()))
    }

    private fun Graphics2D.text(text: String, x: Int, y: Int, size: Float, color: Color, bold: Boolean = false) {
        font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, size.toInt())
        this.color = color
        drawString(text, x, y)
    }

    private fun Graphics2D.titleBar(width: Int, background: Color) {
        this.color = background
        fillRect(0, 0, width, 34)
        for ((i, dot) in listOf(Color(0xC0392B), Color(0xF1C40F), Color(0x27AE60)).withIndex()) {
            this.color = dot
            fillOval(14 + i * 20, 12, 11, 11)
        }
    }

    /** A document window: heading, body text bars and a call-to-action button. */
    fun document(width: Int, height: Int, english: Boolean): BufferedImage = canvas(width, height) {
        color = Color(0xF1F2F5)
        fillRect(0, 0, width, height)
        titleBar(width, Color(0xE8EDF5))
        text(if (english) "Quarterly report" else "Relatório trimestral", 24, 88, 26f, Color(0x4A5876))
        bar(24, 116, width - 70, 14, Color(0x9AA5B8))
        bar(24, 148, width - 160, 14, Color(0x9AA5B8))
        bar(24, 180, width - 100, 14, Color(0x9AA5B8))
        bar(24, 212, width - 220, 14, Color(0x9AA5B8))
        bar(24, 270, 200, 54, Color(0x3D7BE0), arc = 14)
        text(if (english) "Send" else "Enviar", 78, 305, 20f, Color.WHITE)
    }

    /** A settings window with a few switches. */
    fun settingsWindow(width: Int, height: Int, english: Boolean): BufferedImage = canvas(width, height) {
        color = Color(0xFAF7F2)
        fillRect(0, 0, width, height)
        titleBar(width, Color(0xEDE6DD))
        text(if (english) "Preferences" else "Preferências", 24, 78, 24f, ink, bold = true)
        val rows = if (english) listOf("Notifications", "Dark mode", "Auto-update", "Sync") else listOf("Notificações", "Modo escuro", "Atualizar sozinho", "Sincronizar")
        rows.forEachIndexed { i, label ->
            val y = 110 + i * 62
            text(label, 24, y + 24, 18f, ink)
            bar(width - 90, y + 4, 52, 28, if (i % 2 == 0) Color(0xA63F10) else Color(0xC9BFB5), arc = 28)
            color = Color.WHITE
            fillOval(if (i % 2 == 0) width - 62 else width - 86, y + 8, 20, 20)
        }
    }

    /** A dark terminal. */
    fun terminal(width: Int, height: Int): BufferedImage = canvas(width, height) {
        color = Color(0x16181D)
        fillRect(0, 0, width, height)
        titleBar(width, Color(0x2A2E37))
        val lines = listOf("$ ./gradlew desktopTest", "> Task :composeApp:desktopTest", "BUILD SUCCESSFUL in 1m", "$ git push origin main", "main -> main")
        lines.forEachIndexed { i, line ->
            font = Font(Font.MONOSPACED, Font.PLAIN, 16)
            color = if (line.startsWith("$")) Color(0x7EE787) else Color(0xC9D1D9)
            drawString(line, 20, 70 + i * 26)
        }
    }

    /** A whole desktop: wallpaper, a document window, a terminal and a panel, for the selection screen. */
    fun desktop(width: Int, height: Int, english: Boolean): BufferedImage {
        val document = document(560, 360, english)
        val terminal = terminal(430, 250)
        return canvas(width, height) {
            paint = GradientPaint(0f, 0f, Color(0x2B3A67), width.toFloat(), height.toFloat(), Color(0x8E4A6E))
            fillRect(0, 0, width, height)
            drawImage(document, 110, 90, null)
            drawImage(terminal, 600, 300, null)
            color = Color(0xCC111318.toInt(), true)
            fillRect(0, height - 40, width, 40)
            bar(12, height - 32, 24, 24, Color(0xD9531E), arc = 8)
            stroke = BasicStroke(1f)
        }
    }

    fun png(image: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
