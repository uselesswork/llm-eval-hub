package dev.llmevalhub.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseReviewController.class)
class CaseReviewControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    CaseReviewService service;

    @Test
    void savesReviewAndReturnsLocation() throws Exception {
        when(service.create(eq("run-a"), eq("case-a"), any())).thenReturn(
                new CaseReview("review-a", "run-a", "case-a", "alice",
                        ReviewVerdict.NEEDS_FOLLOWUP, "需要核对", Instant.parse("2026-11-19T00:00:00Z")));

        mvc.perform(post("/api/runs/run-a/cases/case-a/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewer":"alice","verdict":"NEEDS_FOLLOWUP","note":"需要核对"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reviews/review-a"))
                .andExpect(jsonPath("$.verdict").value("NEEDS_FOLLOWUP"));
    }

    @Test
    void rejectsBlankReviewer() throws Exception {
        mvc.perform(post("/api/runs/run-a/cases/case-a/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\" \",\"verdict\":\"PASS\"}"))
                .andExpect(status().isBadRequest());
    }
}
