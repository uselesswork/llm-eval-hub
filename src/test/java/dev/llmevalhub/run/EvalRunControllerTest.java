package dev.llmevalhub.run;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvalRunController.class)
class EvalRunControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    EvalRunService service;

    @Test
    void createsRunAndReturnsLocation() throws Exception {
        when(service.create(any())).thenReturn(new EvalRun("test-id", "qa-system", "baseline",
                "dataset-v1", "model-a", null, "LIVE_MODEL", "COMPLETED",
                LocalDateTime.parse("2026-11-18T10:00:00"),
                LocalDateTime.parse("2026-11-18T10:01:00")));

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"sourceProject":"qa-system","runLabel":"baseline","datasetVersion":"dataset-v1",
                 "modelId":"model-a","resultSource":"LIVE_MODEL","status":"COMPLETED",
                 "startedAt":"2026-11-18T10:00:00Z"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/runs/test-id"))
                .andExpect(jsonPath("$.sourceProject").value("qa-system"))
                .andExpect(jsonPath("$.startedAt").value("2026-11-18T10:00:00Z"));
    }

    @Test
    void rejectsMissingProvenance() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"sourceProject":"qa-system","runLabel":"baseline","datasetVersion":"dataset-v1",
                 "status":"COMPLETED","startedAt":"2026-11-18T10:00:00Z"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownRun() throws Exception {
        mvc.perform(get("/api/runs/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsOversizedListLimit() throws Exception {
        mvc.perform(get("/api/runs?limit=101")).andExpect(status().isBadRequest());
    }
}
