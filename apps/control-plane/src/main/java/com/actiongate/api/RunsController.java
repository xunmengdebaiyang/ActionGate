package com.actiongate.api;

import java.net.URI;
import java.util.UUID;

import com.actiongate.control.RunsService;
import com.actiongate.workflow.TicketValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
public class RunsController {
    private final RunsService runs;

    public RunsController(RunsService runs) {
        this.runs = runs;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<RunView> start(@RequestBody String json,
                                       @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        RunView run = runs.start(TicketValidator.parseAndValidate(json), key);
        return ResponseEntity.accepted().location(URI.create("/api/v1/runs/" + run.runId())).body(run);
    }

    @GetMapping("/{runId}")
    public RunView get(@PathVariable UUID runId) {
        return runs.get(runId);
    }

    @PostMapping(path = "/{runId}/approval", consumes = "application/json")
    public ResponseEntity<ApprovalView> approve(@PathVariable UUID runId, @RequestBody String json) {
        ApprovalView approval = ApprovalView.from(runs.approve(runId, ApprovalRequest.parse(json).toApproval(runId)));
        return ResponseEntity.accepted().body(approval);
    }
}
