import com.apollographql.apollo3.api.Optional
import com.intellij.openapi.application.ApplicationManager
import io.vertx.core.json.JsonObject
import monitor.skywalking.protocol.type.Order
import monitor.skywalking.protocol.type.Scope
import monitor.skywalking.protocol.type.TopNCondition
import spp.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.monitor.skywalking.SkywalkingClient.DurationStep
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.findIcon
import spp.plugin.registerIndicator
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class SlowEndpointIndicator : LiveIndicator() {
    override val name = "slow-endpoint"
    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)

    override suspend fun triggerSuspend(guideMark: GuideMark) {
        val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME) ?: return
        val slowestEndpoints = getTop10SlowestEndpoints()

        if (slowestEndpoints.contains(endpointName)) {
            ApplicationManager.getApplication().runReadAction {
                val gutterMark = ArtifactCreationService.createMethodGutterMark(
                        guideMark.sourceFileMarker,
                        (guideMark as MethodSourceMark).getPsiElement().nameIdentifier!!,
                        false
                )
                gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                gutterMark.configuration.icon = findIcon("slow-endpoint/icons/slow-endpoint.svg")
                gutterMark.apply(true)
            }
        }
    }

    private suspend fun getTop10SlowestEndpoints(): List<String> {
        val endTime = ZonedDateTime.now().plusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(30)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val slowestEndpoints = skywalkingMonitorService.sortMetrics(TopNCondition(
                "endpoint_resp_time",
                Optional.presentIfNotNull(skywalkingMonitorService.getCurrentService().name),
                Optional.presentIfNotNull(true),
                Optional.presentIfNotNull(Scope.Endpoint),
                3, //todo: relative 10%
                Order.DES
        ), duration)

        return slowestEndpoints.map { (it as JsonObject).getString("name") }
    }
}

registerIndicator(SlowEndpointIndicator())
