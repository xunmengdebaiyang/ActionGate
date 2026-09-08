package com.actiongate;

import com.actiongate.api.ApiExceptionHandler;
import com.actiongate.api.RunsController;
import com.actiongate.control.RunsService;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TemporalUnavailableTest {
    @Test
    void reportsDependencyFailureWithoutLeakingGrpcDetailsOrPretendingSuccess() throws Exception {
        var service = mock(RunsService.class);
        when(service.start(any())).thenThrow(Status.UNAVAILABLE.withDescription("internal connection details").asRuntimeException());
        var http = MockMvcBuilders.standaloneSetup(new RunsController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        http.perform(post("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"ORDER_STATUS\",\"order_id\":\"10001\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TEMPORAL_UNAVAILABLE"))
                .andExpect(jsonPath("$.run_id").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal connection details"))));
    }
}
