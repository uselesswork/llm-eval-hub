package dev.llmevalhub.usage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsageController.class)
class UsageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsageService service;

    @Test
    void acceptsMissingUsageAsExplicitIncompleteCall() throws Exception {
        when(service.createCall(eq("run-a"), any())).thenReturn(new ModelCall("call-a", "run-a",
                "req-a", "model-a", 100L, null, null, null, null, null,
                "MISSING_USAGE", Instant.EPOCH));
        mvc.perform(post("/api/runs/run-a/calls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"req-a\",\"modelId\":\"model-a\",\"latencyMs\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MISSING_USAGE"));
    }

    @Test
    void rejectsNegativeTokenCount() throws Exception {
        mvc.perform(post("/api/runs/run-a/calls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"req-a\",\"modelId\":\"model-a\",\"inputTokens\":-1,\"outputTokens\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsExactCostAsDecimalString() throws Exception {
        when(service.createCall(eq("run-a"), any())).thenReturn(new ModelCall("call-b", "run-a",
                "req-b", "model-a", 100L, 5000L, 1500L, "v1", "USD",
                new BigDecimal("0.022000000000"), "CALCULATED", Instant.EPOCH));
        mvc.perform(post("/api/runs/run-a/calls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"req-b\",\"modelId\":\"model-a\",\"inputTokens\":5000,\"outputTokens\":1500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cost").value("0.022000000000"));
    }

    @Test
    void rejectsMissingPriceRate() throws Exception {
        mvc.perform(post("/api/prices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelId\":\"model-a\",\"version\":\"v1\",\"currency\":\"USD\",\"inputPerMillion\":1}"))
                .andExpect(status().isBadRequest());
    }
}
