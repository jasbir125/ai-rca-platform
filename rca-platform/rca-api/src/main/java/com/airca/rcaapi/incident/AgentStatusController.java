package com.airca.rcaapi.incident;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Spec section 42: GET /api/agents/status — recent agent executions across incidents,
 *  for the "Agent Activity" UI screen (spec section 24.5). */
@RestController
public class AgentStatusController {

    private final AgentExecutionRepository agentExecutionRepository;

    public AgentStatusController(AgentExecutionRepository agentExecutionRepository) {
        this.agentExecutionRepository = agentExecutionRepository;
    }

    @GetMapping("/api/agents/status")
    public List<AgentExecutionResponse> recentAgentExecutions() {
        return agentExecutionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(AgentExecutionResponse::from)
                .toList();
    }
}
