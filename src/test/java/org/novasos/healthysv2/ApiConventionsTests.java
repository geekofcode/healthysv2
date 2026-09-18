package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.GlobalExceptionHandler;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.novasos.healthysv2.shared.web.CorrelationIdFilter;

class ApiConventionsTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConventionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void versionedPostReturnsCreatedAndLocation() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/convention-resources")
                        .contentType("application/json")
                        .content("""
                                {"name":"Validated resource"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/convention-resources/resource-1"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.name").value("Validated resource"));
    }

    @Test
    void validationUsesTheStandardErrorContract() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/convention-resources")
                        .header(CorrelationIdFilter.HEADER_NAME, "request-123")
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        "request-123"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/convention-resources"))
                .andExpect(jsonPath("$.correlationId").value("request-123"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("must not be blank"));
    }

    @Test
    void malformedJsonUsesTheStandardErrorContract() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/convention-resources")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void domainExceptionReturnsNotFoundWithoutLeakingInternals() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/convention-resources/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("ConventionResource 'missing' was not found"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void invalidIncomingCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/convention-resources/missing")
                        .header(CorrelationIdFilter.HEADER_NAME, "invalid value!"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f-]{36}")));
    }

    @Test
    void pageResponseExposesStableMetadata() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(
                List.of("patient-1", "patient-2"),
                PageRequest.of(1, 2),
                5));

        assertThat(response.content()).containsExactly("patient-1", "patient-2");
        assertThat(response.page().number()).isEqualTo(1);
        assertThat(response.page().size()).isEqualTo(2);
        assertThat(response.page().totalElements()).isEqualTo(5);
        assertThat(response.page().totalPages()).isEqualTo(3);
        assertThat(response.page().first()).isFalse();
        assertThat(response.page().last()).isFalse();
    }

    @RestController
    @RequestMapping(ApiPaths.V1 + "/convention-resources")
    static class ConventionController {

        @PostMapping
        ResponseEntity<ConventionResponse> create(
                @Valid @RequestBody ConventionRequest request) {
            return ResponseEntity
                    .created(URI.create(
                            ApiPaths.V1
                                    + "/convention-resources/resource-1"))
                    .body(new ConventionResponse(
                            "resource-1",
                            request.name()));
        }

        @GetMapping("/missing")
        ConventionResponse missing() {
            throw new ResourceNotFoundException(
                    "ConventionResource",
                    "missing");
        }
    }

    record ConventionRequest(@NotBlank String name) {
    }

    record ConventionResponse(String id, String name) {
    }
}
