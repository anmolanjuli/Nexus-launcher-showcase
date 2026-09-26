package com.nexus.launcher.reader.doc

import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Generates custom CSS overrides for EPUB content driven by NexusColorTokens and ReaderPalette.
 * Ensures consistent theme adaptation across Dark, Light, AMOLED, Calm, E-Ink Paper, and E-Ink Dark Paper modes.
 */
object NexusEpubThemeStyler {

    fun generateCss(palette: NexusReaderThemeHelper.ReaderPalette, tokens: NexusColorTokens): String {
        val bgHex = toHexColor(palette.bg)
        val textHex = toHexColor(palette.textPrimary)
        val accentHex = toHexColor(tokens.accent)
        val dividerHex = toHexColor(palette.divider)
        val isEInk = palette.isEInk

        val fontFamily = if (isEInk) {
            "\"serif\", Georgia, \"Times New Roman\", serif"
        } else {
            "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif"
        }

        val eInkExtras = if (isEInk) {
            """
            * {
                transition: none !important;
                animation: none !important;
                box-shadow: none !important;
                text-shadow: none !important;
                border-radius: 0px !important;
            }
            img, svg, video {
                filter: grayscale(100%) contrast(110%) !important;
            }
            """.trimIndent()
        } else {
            ""
        }

        return """
            <style id="nexus-epub-theme">
            html, body {
                background-color: $bgHex !important;
                color: $textHex !important;
                font-family: $fontFamily !important;
                line-height: 1.6 !important;
                margin: 0 !important;
                padding: 16px 18px 36px 18px !important;
                word-wrap: break-word !important;
                overflow-wrap: break-word !important;
                -webkit-text-size-adjust: 100% !important;
            }
            p, div, span, li {
                color: $textHex !important;
                font-family: $fontFamily !important;
                line-height: 1.6 !important;
            }
            p {
                margin-top: 0 !important;
                margin-bottom: 1.2em !important;
            }
            h1, h2, h3, h4, h5, h6 {
                color: $textHex !important;
                font-family: $fontFamily !important;
                font-weight: bold !important;
                margin-top: 1.5em !important;
                margin-bottom: 0.5em !important;
                line-height: 1.3 !important;
            }
            a, a:link, a:visited {
                color: $accentHex !important;
                text-decoration: underline !important;
            }
            hr {
                border: none !important;
                border-top: 1px solid $dividerHex !important;
                margin: 1.5em 0 !important;
            }
            img, svg, video {
                max-width: 100% !important;
                height: auto !important;
            }
            pre, code {
                background-color: transparent !important;
                color: $textHex !important;
                font-family: monospace !important;
            }
            $eInkExtras
            </style>
        """.trimIndent()
    }

    fun injectIntoHtml(html: String, palette: NexusReaderThemeHelper.ReaderPalette, tokens: NexusColorTokens): String {
        val cssBlock = generateCss(palette, tokens)
        return when {
            html.contains("</head>", ignoreCase = true) -> {
                val index = html.indexOf("</head>", ignoreCase = true)
                html.substring(0, index) + "\n" + cssBlock + "\n" + html.substring(index)
            }
            html.contains("<body", ignoreCase = true) -> {
                val index = html.indexOf("<body", ignoreCase = true)
                html.substring(0, index) + "\n<head>" + cssBlock + "</head>\n" + html.substring(index)
            }
            else -> {
                "<!DOCTYPE html><html><head>$cssBlock</head><body>$html</body></html>"
            }
        }
    }

    private fun toHexColor(colorInt: Int): String {
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }
}
