package com.github.damontecres.wholphin.services.dlna

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Helpers for the XML of the UPnP/DLNA renderer: SOAP requests and responses, DIDL-Lite metadata and the
 * "H:MM:SS" time format.
 */
object DlnaXml {
    const val SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/"
    const val SOAP_ENCODING = "http://schemas.xmlsoap.org/soap/encoding/"

    /** Escape text for an XML element or attribute */
    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Format milliseconds as UPnP time "H:MM:SS", e.g. 0:03:25 or 1:02:03 */
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Parse a UPnP time ("H+:MM:SS[.F+]" or "H+:MM:SS[.F0/F1]", also "MM:SS" or plain seconds) into milliseconds,
     * or null if it is not a time (e.g. "NOT_IMPLEMENTED").
     */
    fun parseTime(value: String?): Long? {
        val text = value?.trim()?.removePrefix("+")
        if (text.isNullOrEmpty()) return null
        val parts = text.split(':')
        if (parts.size > 3) return null
        // Fraction of the last part: either decimal ".123" or "F0/F1"
        val last = parts.last()
        val secondsPart: String
        var fractionMs = 0L
        val dot = last.indexOf('.')
        if (dot >= 0) {
            secondsPart = last.substring(0, dot)
            val fraction = last.substring(dot + 1)
            if (fraction.contains('/')) {
                val (num, den) = fraction.split('/', limit = 2)
                val n = num.toLongOrNull() ?: return null
                val d = den.toLongOrNull() ?: return null
                if (d > 0) fractionMs = n * 1000 / d
            } else if (fraction.isNotEmpty()) {
                if (!fraction.all { it.isDigit() }) return null
                fractionMs = (fraction.padEnd(3, '0').take(3)).toLong()
            }
        } else {
            secondsPart = last
        }
        val numbers = parts.dropLast(1).map { it.toLongOrNull() ?: return null } + (secondsPart.toLongOrNull() ?: return null)
        var seconds = 0L
        for (n in numbers) {
            if (n < 0) return null
            seconds = seconds * 60 + n
        }
        return seconds * 1000 + fractionMs
    }

    internal fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // No DTDs or external entities from the network (not every parser supports the features)
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        ).forEach { (feature, value) ->
            try {
                factory.setFeature(feature, value)
            } catch (_: Exception) {
            }
        }
        try {
            factory.isExpandEntityReferences = false
        } catch (_: Exception) {
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xml.trim().trimStart('\uFEFF'))))
    }

    internal fun Node.localNameOrName(): String = (localName ?: nodeName).substringAfter(':')

    internal fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element) result.add(n)
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------------------
    // SOAP

    /** Parse a SOAP request into its action, service type and arguments. Returns null if it is not a SOAP action. */
    fun parseSoapRequest(xml: String): SoapRequest? {
        val doc =
            try {
                parseXml(xml)
            } catch (_: Exception) {
                return null
            }
        val envelope = doc.documentElement ?: return null
        if (envelope.localNameOrName() != "Envelope") return null
        val body = envelope.childElements().firstOrNull { it.localNameOrName() == "Body" } ?: return null
        val action = body.childElements().firstOrNull() ?: return null
        val args = LinkedHashMap<String, String>()
        action.childElements().forEach { args[it.localNameOrName()] = it.textContent ?: "" }
        return SoapRequest(
            action = action.localNameOrName(),
            serviceType = action.namespaceURI,
            arguments = args,
        )
    }

    /** Build the SOAP response for [action] of [serviceType] with the output [arguments] (in order) */
    fun buildSoapResponse(
        serviceType: String,
        action: String,
        arguments: List<Pair<String, String>> = emptyList(),
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="$SOAP_ENVELOPE_NS" s:encodingStyle="$SOAP_ENCODING">""")
            append("<s:Body>")
            append("""<u:${action}Response xmlns:u="$serviceType">""")
            arguments.forEach { (name, value) ->
                append('<').append(name).append('>')
                append(escape(value))
                append("</").append(name).append('>')
            }
            append("</u:${action}Response>")
            append("</s:Body></s:Envelope>")
        }

    /** Build a SOAP fault with a UPnP error code (sent with HTTP status 500) */
    fun buildSoapFault(
        errorCode: Int,
        description: String,
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="$SOAP_ENVELOPE_NS" s:encodingStyle="$SOAP_ENCODING">""")
            append("<s:Body><s:Fault>")
            append("<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>")
            append("<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">")
            append("<errorCode>").append(errorCode).append("</errorCode>")
            append("<errorDescription>").append(escape(description)).append("</errorDescription>")
            append("</UPnPError></detail>")
            append("</s:Fault></s:Body></s:Envelope>")
        }

    // ---------------------------------------------------------------------------------------------------------
    // DIDL-Lite

    private val tagRegexCache = HashMap<String, Regex>()

    private fun tagRegex(tag: String): Regex =
        synchronized(tagRegexCache) {
            tagRegexCache.getOrPut(tag) {
                Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            }
        }

    private fun unescape(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    /**
     * Parse the DIDL-Lite metadata of a track (e.g. CurrentURIMetaData). Returns null for empty or unusable metadata.
     * Falls back to a lenient regex scan when the XML is not well-formed (some apps do not escape '&' in URLs).
     */
    fun parseDidlLite(xml: String?): DidlMetadata? {
        if (xml.isNullOrBlank() || xml.trim() == "NOT_IMPLEMENTED") return null
        return try {
            parseDidlStrict(xml)
        } catch (_: Exception) {
            parseDidlLenient(xml)
        }
    }

    private fun parseDidlStrict(xml: String): DidlMetadata? {
        val doc = parseXml(xml)
        val root = doc.documentElement ?: return null
        val item =
            root.childElements().firstOrNull { it.localNameOrName() == "item" || it.localNameOrName() == "container" }
                ?: root.takeIf { it.localNameOrName() == "item" }
                ?: return null
        val children = item.childElements()

        fun first(name: String): String? =
            children
                .firstOrNull { it.localNameOrName() == name }
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val artists = children.filter { it.localNameOrName() == "artist" }
        // Prefer the performer, then any artist, then the creator
        val artist =
            artists
                .firstOrNull { it.getAttribute("role").equals("Performer", true) }
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: artists
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: first("creator")
        val res = children.firstOrNull { it.localNameOrName() == "res" }
        return DidlMetadata(
            title = first("title"),
            artist = artist,
            album = first("album"),
            albumArtUri = first("albumArtURI"),
            durationMs = res?.getAttribute("duration")?.let { parseTime(it) },
            protocolInfo = res?.getAttribute("protocolInfo")?.takeIf { it.isNotEmpty() },
            resUri = res?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            upnpClass = first("class"),
        )
    }

    private fun parseDidlLenient(xml: String): DidlMetadata? {
        fun first(tag: String): String? =
            tagRegex(tag)
                .find(xml)
                ?.groupValues
                ?.get(1)
                ?.let { unescape(it).trim() }
                ?.takeIf { it.isNotEmpty() }
        val resMatch =
            Regex("<res(\\s[^>]*)?>(.*?)</res>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xml)
        val resAttrs = resMatch?.groupValues?.get(1).orEmpty()

        fun attr(name: String): String? =
            Regex("$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                .find(resAttrs)
                ?.groupValues
                ?.get(1)
                ?.let { unescape(it) }
        val metadata =
            DidlMetadata(
                title = first("dc:title"),
                artist = first("upnp:artist") ?: first("dc:creator"),
                album = first("upnp:album"),
                albumArtUri = first("upnp:albumArtURI"),
                durationMs = parseTime(attr("duration")),
                protocolInfo = attr("protocolInfo"),
                resUri =
                    resMatch
                        ?.groupValues
                        ?.get(2)
                        ?.let { unescape(it).trim() }
                        ?.takeIf { it.isNotEmpty() },
                upnpClass = first("upnp:class"),
            )
        return metadata.takeIf { it.title != null || it.artist != null || it.resUri != null }
    }

    /** Build DIDL-Lite for a track (for GetMediaInfo / GetPositionInfo / LastChange) */
    fun buildDidlLite(
        uri: String,
        metadata: DidlMetadata?,
    ): String =
        buildString {
            append(
                """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
                    """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""",
            )
            append("""<item id="0" parentID="-1" restricted="1">""")
            append("<dc:title>").append(escape(metadata?.title ?: uri.substringAfterLast('/'))).append("</dc:title>")
            metadata?.artist?.let {
                append("<upnp:artist>").append(escape(it)).append("</upnp:artist>")
                append("<dc:creator>").append(escape(it)).append("</dc:creator>")
            }
            metadata?.album?.let { append("<upnp:album>").append(escape(it)).append("</upnp:album>") }
            metadata?.albumArtUri?.let { append("<upnp:albumArtURI>").append(escape(it)).append("</upnp:albumArtURI>") }
            append("<upnp:class>").append(escape(metadata?.upnpClass ?: "object.item.audioItem.musicTrack")).append("</upnp:class>")
            append("<res")
            metadata?.protocolInfo?.let { append(" protocolInfo=\"").append(escape(it)).append('"') }
            metadata?.durationMs?.let { append(" duration=\"").append(formatTime(it)).append('"') }
            append('>').append(escape(uri)).append("</res>")
            append("</item></DIDL-Lite>")
        }

    /**
     * The mime type from a DLNA protocolInfo ("http-get:*:audio/flac:*"), or null
     */
    fun mimeTypeFromProtocolInfo(protocolInfo: String?): String? =
        protocolInfo
            ?.split(':')
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.contains('/') }
}

data class SoapRequest(
    val action: String,
    val serviceType: String?,
    val arguments: Map<String, String>,
)

data class DidlMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,
    val durationMs: Long? = null,
    val protocolInfo: String? = null,
    val resUri: String? = null,
    val upnpClass: String? = null,
)
