package com.github.damontecres.wholphin.services.dlna

/** The UPnP services of the renderer */
enum class DlnaService(
    val serviceType: String,
    val serviceId: String,
    val path: String,
) {
    AV_TRANSPORT(
        "urn:schemas-upnp-org:service:AVTransport:1",
        "urn:upnp-org:serviceId:AVTransport",
        "AVTransport",
    ),
    RENDERING_CONTROL(
        "urn:schemas-upnp-org:service:RenderingControl:1",
        "urn:upnp-org:serviceId:RenderingControl",
        "RenderingControl",
    ),
    CONNECTION_MANAGER(
        "urn:schemas-upnp-org:service:ConnectionManager:1",
        "urn:upnp-org:serviceId:ConnectionManager",
        "ConnectionManager",
    ),
    ;

    val scpdPath get() = "/$path/scpd.xml"
    val controlPath get() = "/$path/control"
    val eventPath get() = "/$path/event"

    companion object {
        fun fromPath(path: String): DlnaService? = entries.firstOrNull { path.startsWith("/${it.path}/") }
    }
}

/** UPnP AVTransport states */
object TransportStates {
    const val STOPPED = "STOPPED"
    const val PLAYING = "PLAYING"
    const val PAUSED = "PAUSED_PLAYBACK"
    const val TRANSITIONING = "TRANSITIONING"
    const val NO_MEDIA = "NO_MEDIA_PRESENT"
}

/** What the renderer currently plays, as the control points see it */
data class RendererSnapshot(
    val transportState: String = TransportStates.NO_MEDIA,
    val transportStatus: String = "OK",
    val uri: String? = null,
    val metadataXml: String? = null,
    val metadata: DidlMetadata? = null,
    val nextUri: String? = null,
    val nextMetadataXml: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    val muted: Boolean = false,
) {
    val hasMedia get() = uri != null

    /** The actions possible in the current state, for CurrentTransportActions */
    val transportActions: String
        get() =
            when {
                !hasMedia -> ""
                transportState == TransportStates.PLAYING -> "Pause,Stop,Seek"
                transportState == TransportStates.PAUSED -> "Play,Stop,Seek"
                else -> "Play,Stop,Seek"
            }
}

/** A UPnP error returned as a SOAP fault */
class UpnpException(
    val code: Int,
    message: String,
) : Exception(message)

/** The player behind the renderer. Implementations throw [UpnpException] for requests they cannot do. */
interface DlnaRendererControl {
    fun snapshot(): RendererSnapshot

    fun setUri(
        uri: String,
        metadataXml: String?,
        metadata: DidlMetadata?,
    )

    fun setNextUri(
        uri: String?,
        metadataXml: String?,
        metadata: DidlMetadata?,
    )

    fun play()

    fun pause()

    fun stop()

    fun seek(positionMs: Long)

    fun setVolume(volume: Int)

    fun setMute(mute: Boolean)
}

data class SoapResult(
    val httpStatus: Int,
    val body: String,
)

/**
 * Executes the SOAP actions of AVTransport, RenderingControl and ConnectionManager on a [DlnaRendererControl]
 */
class DlnaSoapHandler(
    private val control: DlnaRendererControl,
) {
    fun handle(
        service: DlnaService,
        request: SoapRequest,
    ): SoapResult =
        try {
            val output =
                when (service) {
                    DlnaService.AV_TRANSPORT -> avTransport(request)
                    DlnaService.RENDERING_CONTROL -> renderingControl(request)
                    DlnaService.CONNECTION_MANAGER -> connectionManager(request)
                }
            SoapResult(200, DlnaXml.buildSoapResponse(service.serviceType, request.action, output))
        } catch (ex: UpnpException) {
            SoapResult(500, DlnaXml.buildSoapFault(ex.code, ex.message ?: "Error"))
        } catch (ex: Exception) {
            SoapResult(500, DlnaXml.buildSoapFault(ERROR_ACTION_FAILED, ex.message ?: "Action failed"))
        }

    private fun SoapRequest.arg(name: String): String = arguments[name] ?: throw UpnpException(ERROR_INVALID_ARGS, "Missing argument $name")

    private fun SoapRequest.checkInstance() {
        val id = arguments["InstanceID"]
        if (id != null && id.trim() != "0") throw UpnpException(ERROR_INVALID_INSTANCE, "Invalid InstanceID")
    }

    private fun avTransport(request: SoapRequest): List<Pair<String, String>> {
        request.checkInstance()
        return when (request.action) {
            "SetAVTransportURI" -> {
                val uri = request.arg("CurrentURI").trim()
                if (uri.isEmpty()) throw UpnpException(ERROR_INVALID_ARGS, "Empty CurrentURI")
                val metadataXml = request.arguments["CurrentURIMetaData"]?.takeIf { it.isNotBlank() }
                control.setUri(uri, metadataXml, DlnaXml.parseDidlLite(metadataXml))
                emptyList()
            }

            "SetNextAVTransportURI" -> {
                val uri = request.arg("NextURI").trim().takeIf { it.isNotEmpty() }
                val metadataXml = request.arguments["NextURIMetaData"]?.takeIf { it.isNotBlank() }
                control.setNextUri(uri, metadataXml, DlnaXml.parseDidlLite(metadataXml))
                emptyList()
            }

            "Play" -> {
                if (!control.snapshot().hasMedia) throw UpnpException(ERROR_TRANSITION_NOT_AVAILABLE, "No media")
                control.play()
                emptyList()
            }

            "Pause" -> {
                control.pause()
                emptyList()
            }

            "Stop" -> {
                control.stop()
                emptyList()
            }

            "Seek" -> {
                val unit = request.arg("Unit")
                val target = request.arg("Target")
                if (unit != "REL_TIME" && unit != "ABS_TIME") {
                    throw UpnpException(ERROR_SEEK_MODE_NOT_SUPPORTED, "Seek mode $unit not supported")
                }
                val ms = DlnaXml.parseTime(target) ?: throw UpnpException(ERROR_ILLEGAL_SEEK_TARGET, "Illegal seek target")
                if (!control.snapshot().hasMedia) throw UpnpException(ERROR_TRANSITION_NOT_AVAILABLE, "No media")
                control.seek(ms)
                emptyList()
            }

            "Next", "Previous" -> {
                throw UpnpException(ERROR_TRANSITION_NOT_AVAILABLE, "Transition not available")
            }

            "GetPositionInfo" -> {
                val s = control.snapshot()
                val duration = s.durationMs.takeIf { it > 0 } ?: s.metadata?.durationMs ?: 0L
                val position = DlnaXml.formatTime(s.positionMs)
                listOf(
                    "Track" to if (s.hasMedia) "1" else "0",
                    "TrackDuration" to DlnaXml.formatTime(duration),
                    "TrackMetaData" to (s.uri?.let { s.metadataXml ?: DlnaXml.buildDidlLite(it, s.metadata) } ?: ""),
                    "TrackURI" to (s.uri ?: ""),
                    "RelTime" to position,
                    "AbsTime" to position,
                    "RelCount" to "2147483647",
                    "AbsCount" to "2147483647",
                )
            }

            "GetTransportInfo" -> {
                val s = control.snapshot()
                listOf(
                    "CurrentTransportState" to s.transportState,
                    "CurrentTransportStatus" to s.transportStatus,
                    "CurrentSpeed" to "1",
                )
            }

            "GetMediaInfo" -> {
                val s = control.snapshot()
                val duration = s.durationMs.takeIf { it > 0 } ?: s.metadata?.durationMs ?: 0L
                listOf(
                    "NrTracks" to if (s.hasMedia) "1" else "0",
                    "MediaDuration" to DlnaXml.formatTime(duration),
                    "CurrentURI" to (s.uri ?: ""),
                    "CurrentURIMetaData" to (s.uri?.let { s.metadataXml ?: DlnaXml.buildDidlLite(it, s.metadata) } ?: ""),
                    "NextURI" to (s.nextUri ?: ""),
                    "NextURIMetaData" to (s.nextMetadataXml ?: ""),
                    "PlayMedium" to if (s.hasMedia) "NETWORK" else "NONE",
                    "RecordMedium" to "NOT_IMPLEMENTED",
                    "WriteStatus" to "NOT_IMPLEMENTED",
                )
            }

            "GetDeviceCapabilities" -> {
                listOf(
                    "PlayMedia" to "NETWORK",
                    "RecMedia" to "NOT_IMPLEMENTED",
                    "RecQualityModes" to "NOT_IMPLEMENTED",
                )
            }

            "GetTransportSettings" -> {
                listOf("PlayMode" to "NORMAL", "RecQualityMode" to "NOT_IMPLEMENTED")
            }

            "GetCurrentTransportActions" -> {
                listOf("Actions" to control.snapshot().transportActions)
            }

            else -> {
                throw UpnpException(ERROR_INVALID_ACTION, "Invalid action ${request.action}")
            }
        }
    }

    private fun renderingControl(request: SoapRequest): List<Pair<String, String>> {
        request.checkInstance()
        return when (request.action) {
            "GetVolume" -> {
                listOf("CurrentVolume" to control.snapshot().volume.toString())
            }

            "SetVolume" -> {
                val volume =
                    request.arg("DesiredVolume").trim().toIntOrNull()
                        ?: throw UpnpException(ERROR_INVALID_ARGS, "Invalid volume")
                control.setVolume(volume.coerceIn(0, 100))
                emptyList()
            }

            "GetMute" -> {
                listOf("CurrentMute" to if (control.snapshot().muted) "1" else "0")
            }

            "SetMute" -> {
                val mute =
                    when (request.arg("DesiredMute").trim().lowercase()) {
                        "1", "true", "yes" -> true
                        "0", "false", "no" -> false
                        else -> throw UpnpException(ERROR_INVALID_ARGS, "Invalid mute")
                    }
                control.setMute(mute)
                emptyList()
            }

            "ListPresets" -> {
                listOf("CurrentPresetNameList" to "FactoryDefaults")
            }

            "SelectPreset" -> {
                emptyList()
            }

            else -> {
                throw UpnpException(ERROR_INVALID_ACTION, "Invalid action ${request.action}")
            }
        }
    }

    private fun connectionManager(request: SoapRequest): List<Pair<String, String>> =
        when (request.action) {
            "GetProtocolInfo" -> {
                listOf("Source" to "", "Sink" to SINK_PROTOCOL_INFO)
            }

            "GetCurrentConnectionIDs" -> {
                listOf("ConnectionIDs" to "0")
            }

            "GetCurrentConnectionInfo" -> {
                val id = request.arguments["ConnectionID"]?.trim()
                if (id != null && id != "0") throw UpnpException(ERROR_INVALID_CONNECTION, "Invalid connection reference")
                listOf(
                    "RcsID" to "0",
                    "AVTransportID" to "0",
                    "ProtocolInfo" to "",
                    "PeerConnectionManager" to "",
                    "PeerConnectionID" to "-1",
                    "Direction" to "Input",
                    "Status" to "OK",
                )
            }

            else -> {
                throw UpnpException(ERROR_INVALID_ACTION, "Invalid action ${request.action}")
            }
        }

    companion object {
        const val ERROR_INVALID_ACTION = 401
        const val ERROR_INVALID_ARGS = 402
        const val ERROR_ACTION_FAILED = 501
        const val ERROR_TRANSITION_NOT_AVAILABLE = 701
        const val ERROR_SEEK_MODE_NOT_SUPPORTED = 710
        const val ERROR_ILLEGAL_SEEK_TARGET = 711
        const val ERROR_INVALID_INSTANCE = 718
        const val ERROR_INVALID_CONNECTION = 706

        /** Audio formats the renderer (ExoPlayer) plays */
        val SINK_MIME_TYPES =
            listOf(
                "audio/flac",
                "audio/x-flac",
                "audio/mpeg",
                "audio/mp3",
                "audio/mp4",
                "audio/x-m4a",
                "audio/m4a",
                "audio/aac",
                "audio/x-aac",
                "audio/ogg",
                "audio/opus",
                "audio/vorbis",
                "audio/wav",
                "audio/x-wav",
                "audio/wave",
                "audio/L16;rate=44100;channels=2",
                "audio/L16;rate=48000;channels=2",
                "audio/L16",
                "audio/x-ms-wma",
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
                "application/dash+xml",
                "audio/*",
            )

        val SINK_PROTOCOL_INFO = SINK_MIME_TYPES.joinToString(",") { "http-get:*:$it:*" }
    }
}
