import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.SourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.jetbrains.monitor.skywalking.model.DurationStep
import spp.jetbrains.monitor.skywalking.model.GetEndpointMetrics
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.*
import spp.protocol.SourceServices.Provide.toLiveViewSubscriberAddress
import spp.protocol.artifact.metrics.ArtifactMetrics
import spp.protocol.artifact.metrics.MetricType
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.utils.fromPerSecondToPrettyFrequency
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import spp.protocol.view.LiveViewSubscription
import java.awt.Color
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Displays inlay marks with convenient metrics for a quick overview of the artifact.
 */
class QuickStatsIndicator(project: Project) : LiveIndicator(project) {

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)
    private val inlayForegroundColor = JBColor(Color.decode("#787878"), Color.decode("#787878"))

    /**
     * Wait for [GuideMark] with stats that can be displayed.
     * Currently only endpoints detected by [EndpointDetector] are supported.
     */
    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (EndpointDetector.ENDPOINT_ID != event.params.firstOrNull()) return
        displayQuickStatsInlay(guideMark)
    }

    private suspend fun displayQuickStatsInlay(sourceMark: SourceMark) {
        log.info("Displaying quick stats inlay on artifact: ${sourceMark.artifactQualifiedName.identifier}")
        val swVersion = skywalkingMonitorService.getVersion()
        val listenMetrics = if (swVersion.startsWith("9")) {
            listOf("endpoint_cpm", "endpoint_resp_time", "endpoint_sla")
        } else {
            listOf("endpoint_cpm", "endpoint_avg", "endpoint_sla")
        }
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val metricsRequest = GetEndpointMetrics(
            listenMetrics,
            sourceMark.getUserData(EndpointDetector.ENDPOINT_ID)!!,
            ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        )

        val metrics = skywalkingMonitorService.getMetrics(metricsRequest)
        val inlay = ApplicationManager.getApplication().runReadAction(Computable {
            ArtifactCreationService.createMethodInlayMark(
                sourceMark.sourceFileMarker,
                (sourceMark as MethodSourceMark).getNameIdentifier(),
                false
            )
        })
        inlay.configuration.virtualText = InlayMarkVirtualText(inlay, formatMetricResult(metrics))
        inlay.configuration.virtualText!!.textAttributes.foregroundColor = inlayForegroundColor
        inlay.configuration.virtualText!!.fontSize = -0.5f
        inlay.configuration.virtualText!!.relativeFontSize = true
//        if (PluginBundle.LOCALE.language == "zh") {
//            inlay.configuration.virtualText!!.font = PluginUI.MICROSOFT_YAHEI_PLAIN_14
//            inlay.configuration.virtualText!!.xOffset = 15
//        }
        inlay.configuration.activateOnMouseClick = false
        inlay.apply(true)

        liveViewService.addLiveViewSubscription(
            LiveViewSubscription(
                null,
                listOf(sourceMark.getUserData(EndpointDetector.ENDPOINT_NAME)!!),
                sourceMark.artifactQualifiedName,
                LiveSourceLocation(sourceMark.artifactQualifiedName.identifier, 0), //todo: don't need
                LiveViewConfig("ACTIVITY", listenMetrics, -1)
            )
        ).onComplete {
            if (it.succeeded()) {
                val subscriptionId = it.result().subscriptionId!!
                val previousMetrics = mutableMapOf<Long, String>()
                vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress(subscriptionId)) {
                    val viewEvent = LiveViewEvent(it.body())
                    consumeLiveEvent(viewEvent, previousMetrics)

                    val twoMinAgoValue = previousMetrics[viewEvent.timeBucket.toLong() - 2]
                    if (twoMinAgoValue != null) {
                        inlay.configuration.virtualText!!.updateVirtualText(twoMinAgoValue)
                    }
                }
                inlay.addEventListener {
                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                        liveViewService.removeLiveViewSubscription(subscriptionId)
                    }
                }
            } else {
                show(it.cause().message!!)
            }
        }
    }

    private fun formatMetricResult(artifactMetrics: List<ArtifactMetrics>): String {
        val sb = StringBuilder()
        val resp = artifactMetrics.find { it.metricType == MetricType.Throughput_Average }!!
        val respValue = (resp.values.last() / 60.0).fromPerSecondToPrettyFrequency({ message(it) })
        sb.append(message(resp.metricType.simpleName)).append(": ").append(respValue).append(" | ")
        val cpm = artifactMetrics.find { it.metricType == MetricType.ResponseTime_Average }!!
        sb.append(message(cpm.metricType.simpleName)).append(": ").append(cpm.values.last().toInt())
            .append(message("ms")).append(" | ")
        val sla = artifactMetrics.find { it.metricType == MetricType.ServiceLevelAgreement_Average }!!
        sb.append(message(sla.metricType.simpleName)).append(": ").append(sla.values.last().toDouble() / 100.0)
            .append("%")
        return "$sb"
    }

    private fun consumeLiveEvent(event: LiveViewEvent, previousMetrics: MutableMap<Long, String>) {
        val metrics = JsonArray(event.metricsData)
        val sb = StringBuilder()
        for (i in 0 until metrics.size()) {
            val metric = metrics.getJsonObject(i)
            var value: String? = null
            if (metric.getNumber("percentage") != null) {
                value = (metric.getNumber("percentage").toDouble() / 100.0).toString() + "%"
            }
            if (value == null) value = metric.getNumber("value").toString()

            val metricType = MetricType.realValueOf(metric.getJsonObject("meta").getString("metricsName"))
            if (metricType == MetricType.Throughput_Average) {
                value = (metric.getNumber("value").toDouble() / 60.0).fromPerSecondToPrettyFrequency({ message(it) })
            }
            if (metricType == MetricType.ResponseTime_Average) {
                value += message("ms")
            }
            sb.append("${message(metricType.simpleName)}: $value")
            if (i < metrics.size() - 1) {
                sb.append(" | ")
            }
        }
        previousMetrics[event.timeBucket.toLong()] = "$sb"
    }

    private fun message(message: String): String {
        return message //todo: PluginBundle.message
    }
}

registerIndicator(QuickStatsIndicator(project))
