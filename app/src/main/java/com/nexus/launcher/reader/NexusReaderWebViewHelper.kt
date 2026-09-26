package com.nexus.launcher.reader

import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Handles WebView E-Ink CSS injection and DOM-based article content extraction.
 */
object NexusReaderWebViewHelper {

    /**
     * Turns the publisher's page to paper, and reports when it has. [onApplied] is what a caller
     * waits on before showing the page: the injection is asynchronous, so revealing the WebView
     * first meant a frame or two of full colour every time.
     */
    fun applyWebViewEInkStyling(
        rootLayout: FrameLayout,
        webView: WebView,
        isEInk: Boolean,
        onApplied: (() -> Unit)? = null,
    ) {
        NexusReaderThemeHelper.applyEInkSaturationLayer(rootLayout, isEInk)
        if (isEInk) {
            // The window behind the page is paper too, so the white flash of an empty WebView does
            // not stand in for it while the page loads.
            webView.setBackgroundColor(android.graphics.Color.parseColor("#FAF9F6"))
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var s = document.getElementById('nexus-eink-filter');
                        if (!s) {
                            s = document.createElement('style');
                            s.id = 'nexus-eink-filter';
                            s.innerHTML = 'html, body { filter: grayscale(100%) !important; background-color: #FAF9F6 !important; color: #111111 !important; }';
                            (document.head || document.documentElement).appendChild(s);
                        }
                        return 'ok';
                    } catch (e) { return 'no'; }
                })()
                """.trimIndent()
            ) { onApplied?.invoke() }
            return
        }
        run {
            webView.evaluateJavascript(
                """
                (function() {
                    var s = document.getElementById('nexus-eink-filter');
                    if (s) s.remove();
                })()
                """.trimIndent()
            ) { onApplied?.invoke() }
        }
    }

    /**
     * Attaches WebViewClient and WebChromeClient with progressive page reveal and early DOM extraction.
     * Reveals the page as soon as navigation commits (onPageCommitVisible) or progress reaches 65%,
     * rather than waiting 15-20 seconds for slow ad network trackers in onPageFinished.
     */
    fun attachClients(
        webView: WebView,
        rootLayout: FrameLayout,
        isEInk: () -> Boolean,
        onPageReady: () -> Unit,
        onProgress: (Int) -> Unit,
        onDomExtractReady: () -> Unit
    ) {
        var revealed = false

        fun triggerReveal() {
            if (!revealed) {
                revealed = true
                applyWebViewEInkStyling(rootLayout, webView, isEInk()) {
                    onPageReady()
                }
            }
        }

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                triggerReveal()
                onDomExtractReady()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                triggerReveal()
                onDomExtractReady()
            }
        }

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
                if (newProgress >= 65) {
                    triggerReveal()
                }
                if (newProgress >= 75) {
                    onDomExtractReady()
                }
            }
        }
    }

    fun extractFromDom(
        webView: WebView,
        articleUrl: String,
        articleTitle: String,
        sourceName: String,
        heroImageUrl: String?,
        onArticleExtracted: (ExtractedArticle) -> Unit
    ) {
        webView.evaluateJavascript(
            """
            (function() {
                var el = document.querySelector('article') || document.querySelector('[role="main"]') || document.querySelector('main') || document.querySelector('.article-body, .post-content, .entry-content, .story-body') || document.body;
                var paras = el.querySelectorAll('p, h2, h3, blockquote');
                if (paras.length < 2) {
                    paras = el.querySelectorAll('div > p, section p, div');
                }
                var out = [];
                for (var i = 0; i < paras.length; i++) {
                    var t = paras[i].innerText.trim();
                    if (t.length > 20) out.push(paras[i].tagName.toLowerCase() + '::' + t);
                }
                return out.join('\n--SPLIT--\n');
            })()
            """.trimIndent()
        ) { result ->
            if (result.isNullOrBlank() || result == "null" || result == "\"\"") return@evaluateJavascript
            val unquoted = if (result.startsWith("\"") && result.endsWith("\"")) {
                result.substring(1, result.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
            } else result
            val lines = unquoted.split("\n--SPLIT--\n")
            val elements = mutableListOf<ArticleElement>()
            for (line in lines) {
                val parts = line.split("::", limit = 2)
                if (parts.size == 2) {
                    val tag = parts[0]
                    val text = parts[1].trim()
                    when (tag) {
                        "h2", "h3" -> elements.add(ArticleElement.Heading(text, 2))
                        "blockquote" -> elements.add(ArticleElement.Blockquote(text))
                        else -> elements.add(ArticleElement.Paragraph(text))
                    }
                }
            }
            if (elements.size >= 2) {
                val words = elements.sumOf { (it as? ArticleElement.Paragraph)?.text?.split("\\s+".toRegex())?.size ?: 0 }
                val article = ExtractedArticle(
                    url = articleUrl,
                    title = articleTitle.ifBlank { "Article" },
                    author = sourceName,
                    publishDate = null,
                    heroImageUrl = heroImageUrl,
                    elements = elements,
                    wordCount = words,
                    readingTimeMinutes = maxOf(1, words / 200)
                )
                onArticleExtracted(article)
            }
        }
    }
}
