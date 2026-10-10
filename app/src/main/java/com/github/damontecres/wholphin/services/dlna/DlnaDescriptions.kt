package com.github.damontecres.wholphin.services.dlna

/**
 * The device description, service descriptions (SCPD) and event bodies of the MediaRenderer
 */
object DlnaDescriptions {
    const val DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    private const val AVT_EVENT_NS = "urn:schemas-upnp-org:metadata-1-0/AVT/"
    private const val RCS_EVENT_NS = "urn:schemas-upnp-org:metadata-1-0/RCS/"

    fun deviceDescription(
        friendlyName: String,
        udn: String,
        modelNumber: String,
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">""")
            append("<specVersion><major>1</major><minor>0</minor></specVersion>")
            append("<device>")
            append("<deviceType>").append(DEVICE_TYPE).append("</deviceType>")
            append("<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>")
            append("<friendlyName>").append(DlnaXml.escape(friendlyName)).append("</friendlyName>")
            append("<manufacturer>Wholphinix</manufacturer>")
            append("<manufacturerURL>https://github.com/Yossarian222/Wholphinix</manufacturerURL>")
            append("<modelDescription>Wholphinix music renderer</modelDescription>")
            append("<modelName>Wholphinix</modelName>")
            append("<modelNumber>").append(DlnaXml.escape(modelNumber)).append("</modelNumber>")
            append("<UDN>").append(udn).append("</UDN>")
            append("<serviceList>")
            DlnaService.entries.forEach { service ->
                append("<service>")
                append("<serviceType>").append(service.serviceType).append("</serviceType>")
                append("<serviceId>").append(service.serviceId).append("</serviceId>")
                append("<SCPDURL>").append(service.scpdPath).append("</SCPDURL>")
                append("<controlURL>").append(service.controlPath).append("</controlURL>")
                append("<eventSubURL>").append(service.eventPath).append("</eventSubURL>")
                append("</service>")
            }
            append("</serviceList>")
            append("</device>")
            append("</root>")
        }

    private class Arg(
        val name: String,
        val out: Boolean,
        val variable: String,
    )

    private class Action(
        val name: String,
        val args: List<Arg>,
    )

    private class StateVar(
        val name: String,
        val type: String,
        val evented: Boolean = false,
        val allowed: List<String> = emptyList(),
        val range: IntRange? = null,
    )

    private fun inArg(
        name: String,
        variable: String,
    ) = Arg(name, false, variable)

    private fun outArg(
        name: String,
        variable: String,
    ) = Arg(name, true, variable)

    private val instanceId = inArg("InstanceID", "A_ARG_TYPE_InstanceID")

    private fun scpd(
        actions: List<Action>,
        variables: List<StateVar>,
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<scpd xmlns="urn:schemas-upnp-org:service-1-0">""")
            append("<specVersion><major>1</major><minor>0</minor></specVersion>")
            append("<actionList>")
            actions.forEach { action ->
                append("<action><name>").append(action.name).append("</name>")
                if (action.args.isNotEmpty()) {
                    append("<argumentList>")
                    action.args.forEach { arg ->
                        append("<argument><name>").append(arg.name).append("</name>")
                        append("<direction>").append(if (arg.out) "out" else "in").append("</direction>")
                        append("<relatedStateVariable>").append(arg.variable).append("</relatedStateVariable>")
                        append("</argument>")
                    }
                    append("</argumentList>")
                }
                append("</action>")
            }
            append("</actionList>")
            append("<serviceStateTable>")
            variables.forEach { v ->
                append("<stateVariable sendEvents=\"").append(if (v.evented) "yes" else "no").append("\">")
                append("<name>").append(v.name).append("</name>")
                append("<dataType>").append(v.type).append("</dataType>")
                if (v.allowed.isNotEmpty()) {
                    append("<allowedValueList>")
                    v.allowed.forEach { append("<allowedValue>").append(it).append("</allowedValue>") }
                    append("</allowedValueList>")
                }
                v.range?.let {
                    append("<allowedValueRange><minimum>").append(it.first).append("</minimum>")
                    append("<maximum>").append(it.last).append("</maximum><step>1</step></allowedValueRange>")
                }
                append("</stateVariable>")
            }
            append("</serviceStateTable>")
            append("</scpd>")
        }

    val avTransportScpd: String by lazy {
        scpd(
            actions =
                listOf(
                    Action(
                        "SetAVTransportURI",
                        listOf(
                            instanceId,
                            inArg("CurrentURI", "AVTransportURI"),
                            inArg("CurrentURIMetaData", "AVTransportURIMetaData"),
                        ),
                    ),
                    Action(
                        "SetNextAVTransportURI",
                        listOf(
                            instanceId,
                            inArg("NextURI", "NextAVTransportURI"),
                            inArg("NextURIMetaData", "NextAVTransportURIMetaData"),
                        ),
                    ),
                    Action(
                        "GetMediaInfo",
                        listOf(
                            instanceId,
                            outArg("NrTracks", "NumberOfTracks"),
                            outArg("MediaDuration", "CurrentMediaDuration"),
                            outArg("CurrentURI", "AVTransportURI"),
                            outArg("CurrentURIMetaData", "AVTransportURIMetaData"),
                            outArg("NextURI", "NextAVTransportURI"),
                            outArg("NextURIMetaData", "NextAVTransportURIMetaData"),
                            outArg("PlayMedium", "PlaybackStorageMedium"),
                            outArg("RecordMedium", "RecordStorageMedium"),
                            outArg("WriteStatus", "RecordMediumWriteStatus"),
                        ),
                    ),
                    Action(
                        "GetTransportInfo",
                        listOf(
                            instanceId,
                            outArg("CurrentTransportState", "TransportState"),
                            outArg("CurrentTransportStatus", "TransportStatus"),
                            outArg("CurrentSpeed", "TransportPlaySpeed"),
                        ),
                    ),
                    Action(
                        "GetPositionInfo",
                        listOf(
                            instanceId,
                            outArg("Track", "CurrentTrack"),
                            outArg("TrackDuration", "CurrentTrackDuration"),
                            outArg("TrackMetaData", "CurrentTrackMetaData"),
                            outArg("TrackURI", "CurrentTrackURI"),
                            outArg("RelTime", "RelativeTimePosition"),
                            outArg("AbsTime", "AbsoluteTimePosition"),
                            outArg("RelCount", "RelativeCounterPosition"),
                            outArg("AbsCount", "AbsoluteCounterPosition"),
                        ),
                    ),
                    Action(
                        "GetDeviceCapabilities",
                        listOf(
                            instanceId,
                            outArg("PlayMedia", "PossiblePlaybackStorageMedia"),
                            outArg("RecMedia", "PossibleRecordStorageMedia"),
                            outArg("RecQualityModes", "PossibleRecordQualityModes"),
                        ),
                    ),
                    Action(
                        "GetTransportSettings",
                        listOf(
                            instanceId,
                            outArg("PlayMode", "CurrentPlayMode"),
                            outArg("RecQualityMode", "CurrentRecordQualityMode"),
                        ),
                    ),
                    Action(
                        "GetCurrentTransportActions",
                        listOf(instanceId, outArg("Actions", "CurrentTransportActions")),
                    ),
                    Action("Stop", listOf(instanceId)),
                    Action("Play", listOf(instanceId, inArg("Speed", "TransportPlaySpeed"))),
                    Action("Pause", listOf(instanceId)),
                    Action(
                        "Seek",
                        listOf(instanceId, inArg("Unit", "A_ARG_TYPE_SeekMode"), inArg("Target", "A_ARG_TYPE_SeekTarget")),
                    ),
                    Action("Next", listOf(instanceId)),
                    Action("Previous", listOf(instanceId)),
                ),
            variables =
                listOf(
                    StateVar(
                        "TransportState",
                        "string",
                        allowed =
                            listOf(
                                TransportStates.STOPPED,
                                TransportStates.PLAYING,
                                TransportStates.PAUSED,
                                TransportStates.TRANSITIONING,
                                TransportStates.NO_MEDIA,
                            ),
                    ),
                    StateVar("TransportStatus", "string", allowed = listOf("OK", "ERROR_OCCURRED")),
                    StateVar("PlaybackStorageMedium", "string", allowed = listOf("NONE", "NETWORK")),
                    StateVar("RecordStorageMedium", "string", allowed = listOf("NOT_IMPLEMENTED")),
                    StateVar("PossiblePlaybackStorageMedia", "string"),
                    StateVar("PossibleRecordStorageMedia", "string"),
                    StateVar("CurrentPlayMode", "string", allowed = listOf("NORMAL")),
                    StateVar("TransportPlaySpeed", "string", allowed = listOf("1")),
                    StateVar("RecordMediumWriteStatus", "string", allowed = listOf("NOT_IMPLEMENTED")),
                    StateVar("CurrentRecordQualityMode", "string", allowed = listOf("NOT_IMPLEMENTED")),
                    StateVar("PossibleRecordQualityModes", "string"),
                    StateVar("NumberOfTracks", "ui4"),
                    StateVar("CurrentTrack", "ui4"),
                    StateVar("CurrentTrackDuration", "string"),
                    StateVar("CurrentMediaDuration", "string"),
                    StateVar("CurrentTrackMetaData", "string"),
                    StateVar("CurrentTrackURI", "string"),
                    StateVar("AVTransportURI", "string"),
                    StateVar("AVTransportURIMetaData", "string"),
                    StateVar("NextAVTransportURI", "string"),
                    StateVar("NextAVTransportURIMetaData", "string"),
                    StateVar("RelativeTimePosition", "string"),
                    StateVar("AbsoluteTimePosition", "string"),
                    StateVar("RelativeCounterPosition", "i4"),
                    StateVar("AbsoluteCounterPosition", "i4"),
                    StateVar("CurrentTransportActions", "string"),
                    StateVar("LastChange", "string", evented = true),
                    StateVar("A_ARG_TYPE_SeekMode", "string", allowed = listOf("REL_TIME", "ABS_TIME")),
                    StateVar("A_ARG_TYPE_SeekTarget", "string"),
                    StateVar("A_ARG_TYPE_InstanceID", "ui4"),
                ),
        )
    }

    val renderingControlScpd: String by lazy {
        scpd(
            actions =
                listOf(
                    Action("ListPresets", listOf(instanceId, outArg("CurrentPresetNameList", "PresetNameList"))),
                    Action("SelectPreset", listOf(instanceId, inArg("PresetName", "A_ARG_TYPE_PresetName"))),
                    Action(
                        "GetMute",
                        listOf(instanceId, inArg("Channel", "A_ARG_TYPE_Channel"), outArg("CurrentMute", "Mute")),
                    ),
                    Action(
                        "SetMute",
                        listOf(instanceId, inArg("Channel", "A_ARG_TYPE_Channel"), inArg("DesiredMute", "Mute")),
                    ),
                    Action(
                        "GetVolume",
                        listOf(instanceId, inArg("Channel", "A_ARG_TYPE_Channel"), outArg("CurrentVolume", "Volume")),
                    ),
                    Action(
                        "SetVolume",
                        listOf(instanceId, inArg("Channel", "A_ARG_TYPE_Channel"), inArg("DesiredVolume", "Volume")),
                    ),
                ),
            variables =
                listOf(
                    StateVar("PresetNameList", "string"),
                    StateVar("LastChange", "string", evented = true),
                    StateVar("Mute", "boolean"),
                    StateVar("Volume", "ui2", range = 0..100),
                    StateVar("A_ARG_TYPE_Channel", "string", allowed = listOf("Master")),
                    StateVar("A_ARG_TYPE_InstanceID", "ui4"),
                    StateVar("A_ARG_TYPE_PresetName", "string", allowed = listOf("FactoryDefaults")),
                ),
        )
    }

    val connectionManagerScpd: String by lazy {
        scpd(
            actions =
                listOf(
                    Action(
                        "GetProtocolInfo",
                        listOf(outArg("Source", "SourceProtocolInfo"), outArg("Sink", "SinkProtocolInfo")),
                    ),
                    Action("GetCurrentConnectionIDs", listOf(outArg("ConnectionIDs", "CurrentConnectionIDs"))),
                    Action(
                        "GetCurrentConnectionInfo",
                        listOf(
                            inArg("ConnectionID", "A_ARG_TYPE_ConnectionID"),
                            outArg("RcsID", "A_ARG_TYPE_RcsID"),
                            outArg("AVTransportID", "A_ARG_TYPE_AVTransportID"),
                            outArg("ProtocolInfo", "A_ARG_TYPE_ProtocolInfo"),
                            outArg("PeerConnectionManager", "A_ARG_TYPE_ConnectionManager"),
                            outArg("PeerConnectionID", "A_ARG_TYPE_ConnectionID"),
                            outArg("Direction", "A_ARG_TYPE_Direction"),
                            outArg("Status", "A_ARG_TYPE_ConnectionStatus"),
                        ),
                    ),
                ),
            variables =
                listOf(
                    StateVar("SourceProtocolInfo", "string", evented = true),
                    StateVar("SinkProtocolInfo", "string", evented = true),
                    StateVar("CurrentConnectionIDs", "string", evented = true),
                    StateVar(
                        "A_ARG_TYPE_ConnectionStatus",
                        "string",
                        allowed = listOf("OK", "ContentFormatMismatch", "InsufficientBandwidth", "UnreliableChannel", "Unknown"),
                    ),
                    StateVar("A_ARG_TYPE_ConnectionManager", "string"),
                    StateVar("A_ARG_TYPE_Direction", "string", allowed = listOf("Input", "Output")),
                    StateVar("A_ARG_TYPE_ProtocolInfo", "string"),
                    StateVar("A_ARG_TYPE_ConnectionID", "i4"),
                    StateVar("A_ARG_TYPE_AVTransportID", "i4"),
                    StateVar("A_ARG_TYPE_RcsID", "i4"),
                ),
        )
    }

    fun scpdFor(service: DlnaService): String =
        when (service) {
            DlnaService.AV_TRANSPORT -> avTransportScpd
            DlnaService.RENDERING_CONTROL -> renderingControlScpd
            DlnaService.CONNECTION_MANAGER -> connectionManagerScpd
        }

    private fun propertySet(properties: List<Pair<String, String>>): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">""")
            properties.forEach { (name, value) ->
                append("<e:property><").append(name).append('>')
                append(DlnaXml.escape(value))
                append("</").append(name).append("></e:property>")
            }
            append("</e:propertyset>")
        }

    /** The LastChange XML (not yet escaped) of AVTransport */
    fun avTransportLastChange(s: RendererSnapshot): String {
        val duration = DlnaXml.formatTime(s.durationMs.takeIf { it > 0 } ?: s.metadata?.durationMs ?: 0L)
        val trackMetadata = s.uri?.let { s.metadataXml ?: DlnaXml.buildDidlLite(it, s.metadata) } ?: ""

        fun v(
            name: String,
            value: String,
        ) = "<$name val=\"${DlnaXml.escape(value)}\"/>"
        return buildString {
            append("<Event xmlns=\"").append(AVT_EVENT_NS).append("\"><InstanceID val=\"0\">")
            append(v("TransportState", s.transportState))
            append(v("TransportStatus", s.transportStatus))
            append(v("CurrentPlayMode", "NORMAL"))
            append(v("TransportPlaySpeed", "1"))
            append(v("NumberOfTracks", if (s.hasMedia) "1" else "0"))
            append(v("CurrentTrack", if (s.hasMedia) "1" else "0"))
            append(v("CurrentTrackDuration", duration))
            append(v("CurrentMediaDuration", duration))
            append(v("CurrentTrackURI", s.uri ?: ""))
            append(v("AVTransportURI", s.uri ?: ""))
            append(v("CurrentTrackMetaData", trackMetadata))
            append(v("AVTransportURIMetaData", trackMetadata))
            append(v("NextAVTransportURI", s.nextUri ?: ""))
            append(v("NextAVTransportURIMetaData", s.nextMetadataXml ?: ""))
            append(v("PlaybackStorageMedium", if (s.hasMedia) "NETWORK" else "NONE"))
            append(v("CurrentTransportActions", s.transportActions))
            append("</InstanceID></Event>")
        }
    }

    /** The LastChange XML (not yet escaped) of RenderingControl */
    fun renderingControlLastChange(s: RendererSnapshot): String =
        "<Event xmlns=\"$RCS_EVENT_NS\"><InstanceID val=\"0\">" +
            "<Volume channel=\"Master\" val=\"${s.volume}\"/>" +
            "<Mute channel=\"Master\" val=\"${if (s.muted) "1" else "0"}\"/>" +
            "<PresetNameList val=\"FactoryDefaults\"/>" +
            "</InstanceID></Event>"

    /** The body of a GENA NOTIFY for [service] */
    fun eventBody(
        service: DlnaService,
        s: RendererSnapshot,
    ): String =
        when (service) {
            DlnaService.AV_TRANSPORT -> {
                propertySet(listOf("LastChange" to avTransportLastChange(s)))
            }

            DlnaService.RENDERING_CONTROL -> {
                propertySet(listOf("LastChange" to renderingControlLastChange(s)))
            }

            DlnaService.CONNECTION_MANAGER -> {
                propertySet(
                    listOf(
                        "SourceProtocolInfo" to "",
                        "SinkProtocolInfo" to DlnaSoapHandler.SINK_PROTOCOL_INFO,
                        "CurrentConnectionIDs" to "0",
                    ),
                )
            }
        }
}
