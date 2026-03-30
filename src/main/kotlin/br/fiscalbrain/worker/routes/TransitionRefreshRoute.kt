package br.fiscalbrain.worker.routes

import br.fiscalbrain.transition.TransitionRefreshService
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("worker")
class TransitionRefreshRoute(
    private val transitionRefreshService: TransitionRefreshService
) : RouteBuilder() {
    private val logger = LoggerFactory.getLogger(TransitionRefreshRoute::class.java)

    override fun configure() {
        from("timer:transitionRefresh?period={{app.worker.transition-refresh-interval-ms:3600000}}").routeId("transition-refresh-route")
            .doTry()
                .process {
                    logger.debug("Starting mv_fiscal_impact refresh cycle")
                    transitionRefreshService.refreshMaterializedView()
                }
            .doCatch(Exception::class.java)
                .process { ex ->
                    val err = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable::class.java)
                    logger.error("transition-refresh-route failed during refresh", err)
                }
            .end()
    }
}
