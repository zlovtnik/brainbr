package br.fiscalbrain.api

import br.fiscalbrain.audit.AuditExplainResponse
import br.fiscalbrain.audit.AuditExplainabilityArtifactResponse
import br.fiscalbrain.audit.AuditQueryRequest
import br.fiscalbrain.audit.AuditQueryResponse
import br.fiscalbrain.audit.AuditService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val auditService: AuditService
) {
    @GetMapping("/explain/{skuId}")
    fun explain(@PathVariable skuId: String): AuditExplainResponse = auditService.explain(skuId)

    @GetMapping("/explain/{skuId}/artifact/latest")
    fun explainLatestArtifact(@PathVariable skuId: String): AuditExplainabilityArtifactResponse =
        auditService.explainLatestArtifact(skuId)

    @GetMapping("/explain/artifact/runs/{runId}")
    fun explainByRunId(@PathVariable runId: String): AuditExplainabilityArtifactResponse =
        auditService.explainArtifactByRunId(runId)

    @PostMapping("/query")
    fun query(@Valid @RequestBody request: AuditQueryRequest): AuditQueryResponse =
        auditService.query(request)
}
