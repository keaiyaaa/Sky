package com.example.skybackend.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiTokenFilterTest {

    @Test
    void rejectsRequestsWhenServerTokenIsMissing() throws Exception {
        MockHttpServletResponse response = sendRequest(new ApiTokenFilter(""), "/api/v1/intent/analyze", null);

        assertEquals(503, response.getStatus());
    }

    @Test
    void rejectsRequestsWithoutTheConfiguredBearerToken() throws Exception {
        MockHttpServletResponse response = sendRequest(
                new ApiTokenFilter("test-token"), "/api/v1/intent/analyze", "Bearer wrong-token"
        );

        assertEquals(401, response.getStatus());
    }

    @Test
    void allowsRequestsWithTheConfiguredBearerToken() throws Exception {
        MockHttpServletResponse response = sendRequest(
                new ApiTokenFilter("test-token"), "/api/v1/intent/analyze", "Bearer test-token"
        );

        assertEquals(200, response.getStatus());
    }

    private static MockHttpServletResponse sendRequest(ApiTokenFilter filter, String path, String authorization)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
