package dev.llmevalhub.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobReportController.class)
class JobReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobReportService service;

    private static final String VALID = """
            {"sourceRunId":"eval-1","datasetSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "runLabel":"fixture","resultSource":"OFFLINE_FIXTURE","startedAt":"2026-11-20T00:00:00Z",
             "cases":[{"id":"resume-1","task":"resume","passed":true,
                       "checks":[{"name":"schema_valid","passed":true}]}]}
            """;

    @Test
    void importsSanitizedReport() throws Exception {
        when(service.importReport(any())).thenReturn(new JobReportService.ImportOutcome("run-1", "hash", true));
        mvc.perform(post("/api/imports/job-agent").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value("run-1"));
    }

    @Test
    void rejectsMissingChecks() throws Exception {
        mvc.perform(post("/api/imports/job-agent").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("[{\"name\":\"schema_valid\",\"passed\":true}]", "[]")))
                .andExpect(status().isBadRequest());
    }
}
