package br.fiscalbrain.api

import br.fiscalbrain.transition.EffectiveRateQuery
import br.fiscalbrain.transition.EffectiveRateResponse
import br.fiscalbrain.transition.TransitionCalendarResponse
import br.fiscalbrain.transition.TransitionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/transition")
class TransitionController(
    private val transitionService: TransitionService
) {

    @GetMapping("/calendar")
    fun calendar(): TransitionCalendarResponse = transitionService.calendar()

    @GetMapping("/effective-rate/{skuId}")
    fun effectiveRate(
        @PathVariable skuId: String,
        @Valid @ModelAttribute query: EffectiveRateQuery
    ): EffectiveRateResponse = transitionService.effectiveRate(skuId, query.year)
}
