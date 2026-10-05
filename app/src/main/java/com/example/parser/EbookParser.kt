package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.ChapterEntity
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val TAG = "EbookParser"

data class ParsedBook(
    val title: String,
    val author: String,
    val format: String,
    val coverImagePath: String? = null,
    val chapters: List<ChapterEntity>
)

object EbookParser {

    /**
     * Parses an eBook from an Android Uri or InputStream.
     */
    fun parseUri(context: Context, uri: Uri, fileName: String): ParsedBook {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")

        return inputStream.use { stream ->
            when (extension) {
                "epub" -> parseEpub(context, stream, fileName)
                "pdf" -> parsePdf(context, stream, fileName)
                "txt" -> parseText(stream, fileName, "TXT")
                "md" -> parseText(stream, fileName, "MD")
                "htm", "html" -> parseHtml(stream, fileName)
                else -> parseText(stream, fileName, "TXT")
            }
        }
    }

    /**
     * Parse an EPUB archive with cover image and chapter detection.
     */
    fun parseEpub(context: Context, inputStream: InputStream, defaultTitle: String): ParsedBook {
        val zip = ZipInputStream(inputStream)
        val fileEntries = mutableMapOf<String, ByteArray>()
        var entry: ZipEntry? = zip.nextEntry

        while (entry != null) {
            if (!entry.isDirectory) {
                fileEntries[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        // 1. Locate container.xml
        val containerXml = fileEntries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        var opfPath = "OEBPS/content.opf"
        if (containerXml != null) {
            val rootFileRegex = Regex("""full-path\s*=\s*["']([^"']+)["']""")
            rootFileRegex.find(containerXml)?.let {
                opfPath = it.groupValues[1]
            }
        }

        val opfContent = fileEntries[opfPath]?.toString(Charsets.UTF_8)
            ?: fileEntries.entries.firstOrNull { it.key.endsWith(".opf", ignoreCase = true) }?.value?.toString(Charsets.UTF_8)

        var title = defaultTitle.removeSuffix(".epub").replace("_", " ")
        var author = "Autor Desconhecido"
        var coverImagePath: String? = null

        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""
        val chapterHtmlFiles = mutableListOf<String>()
        val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
        var coverItemId: String? = null

        if (opfContent != null) {
            // Extract Title
            val titleRegex = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            titleRegex.find(opfContent)?.let {
                val clean = cleanHtml(it.groupValues[1]).trim()
                if (clean.isNotBlank()) title = clean
            }

            // Extract Author
            val creatorRegex = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
            creatorRegex.find(opfContent)?.let {
                val clean = cleanHtml(it.groupValues[1]).trim()
                if (clean.isNotBlank()) author = clean
            }

            // Look for cover in meta
            val metaCoverRegex = Regex("""<meta\s+[^>]*name=["']cover["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            metaCoverRegex.find(opfContent)?.let {
                coverItemId = it.groupValues[1]
            }

            // Extract manifest items
            val itemRegex = Regex("""<item\s+([^>]+)/>""", RegexOption.IGNORE_CASE)
            val attrRegex = Regex("""([a-zA-Z0-9_\-:]+)=["']([^"']+)["']""")

            itemRegex.findAll(opfContent).forEach { match ->
                val tagContent = match.groupValues[1]
                var id = ""
                var href = ""
                var mediaType = ""
                var properties = ""

                attrRegex.findAll(tagContent).forEach { attr ->
                    when (attr.groupValues[1].lowercase()) {
                        "id" -> id = attr.groupValues[2]
                        "href" -> href = attr.groupValues[2]
                        "media-type" -> mediaType = attr.groupValues[2].lowercase()
                        "properties" -> properties = attr.groupValues[2].lowercase()
                    }
                }

                if (id.isNotBlank() && href.isNotBlank()) {
                    manifest[id] = Pair(href, mediaType)
                    if (properties.contains("cover-image") || id.equals("cover", ignoreCase = true) || id.equals("cover-image", ignoreCase = true)) {
                        coverItemId = id
                    }
                }
            }

            // Extract spine order
            val itemrefRegex = Regex("""<itemref\s+[^>]*idref=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            itemrefRegex.findAll(opfContent).forEach { match ->
                val idref = match.groupValues[1]
                manifest[idref]?.let { (href, mediaType) ->
                    if (mediaType.contains("xhtml") || mediaType.contains("html") || href.endsWith(".xhtml", true) || href.endsWith(".html", true)) {
                        chapterHtmlFiles.add(opfDir + href)
                    }
                }
            }

            // Try to extract cover image
            coverImagePath = extractAndSaveCover(context, fileEntries, opfDir, coverItemId, manifest)
        }

        // If no spine was resolved, grab all xhtml/html files sorted
        if (chapterHtmlFiles.isEmpty()) {
            fileEntries.keys
                .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) }
                .sorted()
                .forEach { chapterHtmlFiles.add(it) }
        }

        // Parse TOC / NCX for precise chapter titles if available
        val tocTitles = parseTocTitles(fileEntries, opfDir)

        val chapters = mutableListOf<ChapterEntity>()
        var chapterIndex = 0

        for (filePath in chapterHtmlFiles) {
            val fileName = filePath.substringAfterLast('/')
            val rawBytes = fileEntries[filePath]
                ?: fileEntries[filePath.substringAfter('/')]
                ?: fileEntries.entries.firstOrNull { it.key.endsWith(fileName) }?.value

            if (rawBytes != null) {
                val html = rawBytes.toString(Charsets.UTF_8)
                val chapterText = extractTextFromHtml(html, context, fileEntries, filePath)
                if (chapterText.isNotBlank()) {
                    val resolvedTitle = tocTitles[fileName]
                        ?: extractChapterTitle(html)
                        ?: "Capítulo ${chapterIndex + 1}"

                    chapters.add(
                        ChapterEntity(
                            bookId = 0,
                            chapterIndex = chapterIndex++,
                            title = resolvedTitle,
                            content = chapterText
                        )
                    )
                }
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(
                ChapterEntity(
                    bookId = 0,
                    chapterIndex = 0,
                    title = "Capítulo 1",
                    content = "Não foi possível carregar os capítulos do arquivo EPUB."
                )
            )
        }

        return ParsedBook(
            title = title,
            author = author,
            format = "EPUB",
            coverImagePath = coverImagePath,
            chapters = chapters
        )
    }

    private fun extractAndSaveCover(
        context: Context,
        fileEntries: Map<String, ByteArray>,
        opfDir: String,
        coverItemId: String?,
        manifest: Map<String, Pair<String, String>>
    ): String? {
        try {
            var coverHref: String? = null
            if (coverItemId != null && manifest.containsKey(coverItemId)) {
                coverHref = manifest[coverItemId]?.first
            }

            // If not found by ID, look for obvious image names
            if (coverHref == null) {
                coverHref = manifest.values.firstOrNull { (href, mediaType) ->
                    mediaType.startsWith("image/") && (href.contains("cover", ignoreCase = true) || href.contains("capa", ignoreCase = true))
                }?.first
            }

            var coverBytes: ByteArray? = null
            if (coverHref != null) {
                val fullPath = opfDir + coverHref
                coverBytes = fileEntries[fullPath]
                    ?: fileEntries[coverHref]
                    ?: fileEntries.entries.firstOrNull { it.key.endsWith(coverHref.substringAfterLast('/')) }?.value
            }

            // Fallback: check file entries directly
            if (coverBytes == null) {
                coverBytes = fileEntries.entries.firstOrNull { entry ->
                    val k = entry.key.lowercase()
                    (k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png")) &&
                    (k.contains("cover") || k.contains("capa"))
                }?.value
            }

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val coversDir = File(context.filesDir, "covers")
                if (!coversDir.exists()) coversDir.mkdirs()
                val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(coverFile).use { it.write(coverBytes) }
                return coverFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao extrair capa do EPUB: ${e.message}")
        }
        return null
    }

    private fun parseTocTitles(fileEntries: Map<String, ByteArray>, opfDir: String): Map<String, String> {
        val titlesMap = mutableMapOf<String, String>()
        try {
            val ncxEntry = fileEntries.entries.firstOrNull { it.key.endsWith(".ncx", true) }
            if (ncxEntry != null) {
                val ncxContent = ncxEntry.value.toString(Charsets.UTF_8)
                val navPointRegex = Regex(
                    """<navPoint[^>]*>.*?<navLabel>\s*<text>(.*?)</text>\s*</navLabel>\s*<content\s+src=["']([^"']+)["']""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                )

                navPointRegex.findAll(ncxContent).forEach { match ->
                    val label = cleanHtml(match.groupValues[1]).trim()
                    val src = match.groupValues[2].substringBefore('#').substringAfterLast('/')
                    if (label.isNotBlank() && src.isNotBlank() && !titlesMap.containsKey(src)) {
                        titlesMap[src] = label
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao analisar TOC NCX: ${e.message}")
        }
        return titlesMap
    }

    /**
     * Parses standard TXT or Markdown files, segmenting by chapter headings.
     */
    fun parseText(inputStream: InputStream, defaultTitle: String, format: String = "TXT"): ParsedBook {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val fullText = reader.readText()

        val title = defaultTitle.substringBeforeLast('.').replace("_", " ")
        val author = "Desconhecido"

        val chapters = splitIntoChapters(fullText)

        return ParsedBook(
            title = title,
            author = author,
            format = format,
            chapters = chapters
        )
    }

    /**
     * Parses HTML files.
     */
    fun parseHtml(inputStream: InputStream, defaultTitle: String): ParsedBook {
        val rawHtml = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val text = extractTextFromHtml(rawHtml)
        val title = extractChapterTitle(rawHtml) ?: defaultTitle.substringBeforeLast('.')
        val chapters = splitIntoChapters(text)

        return ParsedBook(
            title = title,
            author = "Desconhecido",
            format = "HTML",
            chapters = chapters
        )
    }

    /**
     * Segments text into chapters using enhanced chapter detection patterns.
     */
    private fun splitIntoChapters(rawText: String): List<ChapterEntity> {
        val lines = rawText.lines()
        val chapters = mutableListOf<ChapterEntity>()

        val chapterHeaderRegex = Regex(
            """^(?:#+\s+|[Cc]ap[ií]tulo\s+(?:\d+|[IVXLCDM]+|[A-Za-zçãõéêíóú]+)|[Cc]hapter\s+\d+|[Pp]arte\s+(?:\d+|[IVXLCDM]+)|[Ll]ivro\s+(?:\d+|[IVXLCDM]+)|[Pp]r[oó]logo|[Ee]p[ií]logo|[Ii]ntrodu[çc][aã]o|[Pp]ref[aá]cio|[Aa]to\s+[IVXLCDM]+|[Cc]ena\s+\d+|[IVXLCDM]{1,6}\b)""",
            RegexOption.IGNORE_CASE
        )

        var currentChapterTitle = "Início"
        val currentParagraphs = mutableListOf<String>()
        var chapterIndex = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && chapterHeaderRegex.containsMatchIn(trimmed) && trimmed.length < 80) {
                if (currentParagraphs.isNotEmpty()) {
                    chapters.add(
                        ChapterEntity(
                            bookId = 0,
                            chapterIndex = chapterIndex++,
                            title = currentChapterTitle,
                            content = currentParagraphs.joinToString("\n\n")
                        )
                    )
                    currentParagraphs.clear()
                }
                currentChapterTitle = trimmed.removePrefix("#").trim()
            } else if (trimmed.isNotBlank()) {
                currentParagraphs.add(trimmed)
            }
        }

        if (currentParagraphs.isNotEmpty()) {
            chapters.add(
                ChapterEntity(
                    bookId = 0,
                    chapterIndex = chapterIndex,
                    title = currentChapterTitle,
                    content = currentParagraphs.joinToString("\n\n")
                )
            )
        }

        // If no chapters were identified and the text is large, chunk cleanly
        if (chapters.size <= 1 && currentParagraphs.size > 40) {
            val chunked = currentParagraphs.chunked(30)
            return chunked.mapIndexed { idx, pars ->
                ChapterEntity(
                    bookId = 0,
                    chapterIndex = idx,
                    title = "Capítulo ${idx + 1}",
                    content = pars.joinToString("\n\n")
                )
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(
                ChapterEntity(
                    bookId = 0,
                    chapterIndex = 0,
                    title = "Texto Completo",
                    content = rawText.trim()
                )
            )
        }

        return chapters
    }

    private fun extractChapterTitle(html: String): String? {
        val h1Regex = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.IGNORE_CASE)
        h1Regex.find(html)?.let {
            val cleaned = cleanHtml(it.groupValues[1]).trim()
            if (cleaned.isNotBlank() && cleaned.length < 80) {
                return cleaned
            }
        }
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
        titleRegex.find(html)?.let {
            val cleaned = cleanHtml(it.groupValues[1]).trim()
            if (cleaned.isNotBlank() && cleaned.length < 80) return cleaned
        }
        return null
    }

    private fun extractTextFromHtml(
        html: String,
        context: Context? = null,
        fileEntries: Map<String, ByteArray>? = null,
        htmlPath: String? = null
    ): String {
        var processedHtml = html

        // Extract embedded illustrations if context and fileEntries are available
        if (context != null && fileEntries != null && htmlPath != null) {
            val htmlDir = if (htmlPath.contains('/')) htmlPath.substringBeforeLast('/') + "/" else ""
            val imgRegex = Regex("""<(?:img|image)[^>]*(?:src|href|xlink:href)=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            processedHtml = imgRegex.replace(html) { match ->
                val rawSrc = match.groupValues[1].substringBefore('#').substringBefore('?')
                if (rawSrc.isBlank() || rawSrc.startsWith("data:")) return@replace ""

                val imgBytes = resolveZipEntry(fileEntries, htmlDir, rawSrc)
                if (imgBytes != null && imgBytes.isNotEmpty()) {
                    val savedPath = saveExtractedImage(context, rawSrc, imgBytes)
                    if (savedPath != null) {
                        "\n\n[IMG:$savedPath]\n\n"
                    } else ""
                } else ""
            }
        }

        var clean = processedHtml.replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        clean = clean.replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        clean = clean.replace(Regex("""<(?:br|p|div|h[1-6]|li)[^>]*>""", RegexOption.IGNORE_CASE), "\n\n")
        clean = cleanHtml(clean)

        return clean.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun resolveZipEntry(
        fileEntries: Map<String, ByteArray>,
        baseDir: String,
        relativeSrc: String
    ): ByteArray? {
        val cleanSrc = relativeSrc.replace('\\', '/')
        // 1. Direct path with baseDir
        val directPath = normalizeZipPath(baseDir + cleanSrc)
        if (fileEntries.containsKey(directPath)) {
            return fileEntries[directPath]
        }
        // 2. Relative directly
        if (fileEntries.containsKey(cleanSrc)) {
            return fileEntries[cleanSrc]
        }
        // 3. Fallback: match by filename
        val fileName = cleanSrc.substringAfterLast('/')
        return fileEntries.entries.firstOrNull { it.key.endsWith("/$fileName") || it.key.equals(fileName, ignoreCase = true) }?.value
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path.split('/')
        val resolved = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }

    private fun saveExtractedImage(context: Context, srcName: String, bytes: ByteArray): String? {
        return try {
            val imagesDir = File(context.filesDir, "book_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val ext = srcName.substringAfterLast('.', "jpg").take(4)
            val hash = (srcName + bytes.size).hashCode().toString().replace("-", "n")
            val destFile = File(imagesDir, "img_${hash}.$ext")
            if (!destFile.exists()) {
                FileOutputStream(destFile).use { it.write(bytes) }
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanHtml(text: String): String {
        return text.replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&atilde;", "ã")
            .replace("&otilde;", "õ")
            .replace("&eacute;", "é")
            .replace("&aacute;", "á")
            .replace("&iacute;", "í")
            .replace("&oacute;", "ó")
            .replace("&uacute;", "ú")
            .replace("&ccedil;", "ç")
            .trim()
    }

    /**
     * Parses a PDF document, renders page 1 as cover, and extracts text content into chapters.
     */
    fun parsePdf(context: Context, inputStream: InputStream, defaultTitle: String): ParsedBook {
        val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
        val bytes = try {
            FileOutputStream(tempFile).use { fos ->
                inputStream.copyTo(fos)
            }
            tempFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar PDF temporário", e)
            throw IllegalArgumentException("Não foi possível ler os dados do PDF: ${e.message}")
        }

        // 1. Render First Page as Cover via Android PdfRenderer
        var coverImagePath: String? = null
        try {
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(pfd)
            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val width = (page.width * 1.5f).toInt().coerceIn(300, 1080)
                val height = (page.height * 1.5f).toInt().coerceIn(400, 1920)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val coverFile = File(context.filesDir, "cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(coverFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                coverImagePath = coverFile.absolutePath
            }
            pdfRenderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gerar capa com PdfRenderer", e)
        } finally {
            tempFile.delete()
        }

        // 2. Extract Metadata (Title & Author)
        val fileContentIso = String(bytes, Charsets.ISO_8859_1)
        var title = defaultTitle.removeSuffix(".pdf").replace("_", " ")
        var author = "Autor Desconhecido"

        val titleRegex = Regex("""/Title\s*(?:\(([^()]+)\)|<([0-9A-Fa-f]+)>)""")
        titleRegex.find(fileContentIso)?.let { match ->
            val literal = match.groups[1]?.value
            val hex = match.groups[2]?.value
            val extracted = if (literal != null) decodePdfString(literal) else if (hex != null) decodeHexPdfString(hex) else null
            if (!extracted.isNullOrBlank()) title = extracted.trim()
        }

        val authorRegex = Regex("""/Author\s*(?:\(([^()]+)\)|<([0-9A-Fa-f]+)>)""")
        authorRegex.find(fileContentIso)?.let { match ->
            val literal = match.groups[1]?.value
            val hex = match.groups[2]?.value
            val extracted = if (literal != null) decodePdfString(literal) else if (hex != null) decodeHexPdfString(hex) else null
            if (!extracted.isNullOrBlank()) author = extracted.trim()
        }

        // 3. Extract Text Streams
        val pageTexts = mutableListOf<String>()
        val streamRegex = Regex("""(?s)<<(.*?)>>\s*stream\r?\n(.*?)\r?\nendstream""")
        val matches = streamRegex.findAll(fileContentIso)

        for (match in matches) {
            val dict = match.groupValues[1]
            val streamStartIndex = match.groups[2]?.range?.first ?: continue
            val streamEndIndex = match.groups[2]?.range?.last ?: continue

            if (streamEndIndex < streamStartIndex || streamEndIndex >= bytes.size) continue

            val rawStreamBytes = bytes.copyOfRange(streamStartIndex, streamEndIndex + 1)
            val isFlate = dict.contains("/FlateDecode")

            val decompressedBytes = if (isFlate) {
                decompressFlate(rawStreamBytes)
            } else {
                rawStreamBytes
            }

            if (decompressedBytes.isNotEmpty()) {
                val streamStr = String(decompressedBytes, Charsets.ISO_8859_1)
                if (streamStr.contains("BT") && streamStr.contains("ET")) {
                    val pageText = extractTextFromPdfContentStream(streamStr)
                    if (pageText.isNotBlank()) {
                        pageTexts.add(pageText)
                    }
                }
            }
        }

        // 4. Build Chapters
        val chapters = mutableListOf<ChapterEntity>()
        if (pageTexts.isNotEmpty()) {
            var currentChapterText = StringBuilder()
            var currentChapterTitle = "Início"
            var currentChapterIdx = 0

            for ((i, pageText) in pageTexts.withIndex()) {
                val headingRegex = Regex("""(?im)^(Cap[ií]tulo\s+\w+|Chapter\s+\w+|Parte\s+\w+|[0-9]+\.\s+[A-ZÀ-Ú].{2,40})""")
                val foundHeading = headingRegex.find(pageText)?.value

                if (foundHeading != null && currentChapterText.isNotBlank()) {
                    chapters.add(
                        ChapterEntity(
                            bookId = 0,
                            chapterIndex = currentChapterIdx++,
                            title = currentChapterTitle,
                            content = currentChapterText.toString().trim()
                        )
                    )
                    currentChapterText = StringBuilder()
                    currentChapterTitle = foundHeading.trim()
                } else if (currentChapterText.length > 12000) {
                    chapters.add(
                        ChapterEntity(
                            bookId = 0,
                            chapterIndex = currentChapterIdx++,
                            title = currentChapterTitle,
                            content = currentChapterText.toString().trim()
                        )
                    )
                    currentChapterText = StringBuilder()
                    currentChapterTitle = "Seção ${currentChapterIdx + 1}"
                }

                if (currentChapterText.isEmpty() && currentChapterTitle == "Início") {
                    currentChapterTitle = "Página ${i + 1}"
                }

                currentChapterText.append(pageText).append("\n\n")
            }

            if (currentChapterText.isNotBlank()) {
                chapters.add(
                    ChapterEntity(
                        bookId = 0,
                        chapterIndex = currentChapterIdx,
                        title = currentChapterTitle,
                        content = currentChapterText.toString().trim()
                    )
                )
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(
                ChapterEntity(
                    bookId = 0,
                    chapterIndex = 0,
                    title = "PDF Importado",
                    content = """Este documento PDF foi importado com sucesso na biblioteca.
                    
Caso as palavras não apareçam para leitura, o arquivo pode ser composto por páginas escaneadas como imagem sem camada OCR de texto. Para leitura fluida e narração TTS, utilize PDFs com texto selecionável ou arquivos nos formatos EPUB e TXT."""
                )
            )
        }

        return ParsedBook(
            title = title,
            author = author,
            format = "PDF",
            coverImagePath = coverImagePath,
            chapters = chapters
        )
    }

    private fun extractTextFromPdfContentStream(streamStr: String): String {
        val result = StringBuilder()
        val btEtRegex = Regex("""(?s)BT\s*(.*?)\s*ET""")
        val textBlocks = btEtRegex.findAll(streamStr)

        for (block in textBlocks) {
            val content = block.groupValues[1]
            val opRegex = Regex("""(\((?:[^()\\]|\\.)*\))\s*Tj|\[(.*?)\]\s*TJ|(\((?:[^()\\]|\\.)*\))\s*['"]|<([0-9A-Fa-f\s]+)>\s*Tj|(?:\r?\n|T\*)""")
            val opMatches = opRegex.findAll(content)

            for (op in opMatches) {
                when {
                    op.groups[1] != null -> {
                        val rawStr = op.groups[1]!!.value
                        result.append(decodePdfString(rawStr.removeSurrounding("(", ")")))
                    }
                    op.groups[2] != null -> {
                        val arrayContent = op.groups[2]!!.value
                        val itemRegex = Regex("""\((?:[^()\\]|\\.)*\)|<[0-9A-Fa-f\s]+>|(-?\d+(?:\.\d+)?)""")
                        val items = itemRegex.findAll(arrayContent)
                        for (item in items) {
                            val v = item.value
                            if (v.startsWith("(") && v.endsWith(")")) {
                                result.append(decodePdfString(v.removeSurrounding("(", ")")))
                            } else if (v.startsWith("<") && v.endsWith(">")) {
                                result.append(decodeHexPdfString(v.removeSurrounding("<", ">")))
                            } else {
                                val num = v.toDoubleOrNull()
                                if (num != null && num < -140.0) {
                                    result.append(" ")
                                }
                            }
                        }
                    }
                    op.groups[3] != null -> {
                        val rawStr = op.groups[3]!!.value
                        result.append("\n").append(decodePdfString(rawStr.removeSurrounding("(", ")")))
                    }
                    op.groups[4] != null -> {
                        val hex = op.groups[4]!!.value
                        result.append(decodeHexPdfString(hex))
                    }
                    else -> {
                        result.append(" ")
                    }
                }
            }
            result.append("\n\n")
        }

        return result.toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun decodePdfString(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '\\', '(', ')' -> { sb.append(next); i += 2 }
                    in '0'..'7' -> {
                        var octalLen = 1
                        while (octalLen < 3 && i + 1 + octalLen <= raw.length && raw[i + octalLen] in '0'..'7') {
                            octalLen++
                        }
                        val octalStr = raw.substring(i + 1, i + octalLen)
                        val code = octalStr.toIntOrNull(8) ?: 32
                        sb.append(code.toChar())
                        i += octalLen
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        val text = sb.toString()
        if (text.startsWith("\u00FE\u00FF")) {
            return try {
                val bytes = text.substring(2).map { it.code.toByte() }.toByteArray()
                String(bytes, Charsets.UTF_16BE)
            } catch (_: Exception) {
                text.substring(2)
            }
        }
        return text
    }

    private fun decodeHexPdfString(hex: String): String {
        val clean = hex.replace(Regex("""\s+"""), "")
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            bytes[i] = clean.substring(index, index + 2).toIntOrNull(16)?.toByte() ?: 0
        }
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            try {
                String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
            } catch (_: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
        } else {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun decompressFlate(data: ByteArray): ByteArray {
        return try {
            val inflater = Inflater(false)
            val bis = ByteArrayInputStream(data)
            val iis = InflaterInputStream(bis, inflater)
            iis.readBytes()
        } catch (_: Exception) {
            try {
                val inflater = Inflater(true)
                val bis = ByteArrayInputStream(data)
                val iis = InflaterInputStream(bis, inflater)
                iis.readBytes()
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }
}
