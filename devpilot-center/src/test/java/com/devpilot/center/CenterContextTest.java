package com.devpilot.center;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CenterContextTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void applicationStartsAndServesUiAndApis() {
        var index = rest.getForEntity("/", String.class);
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(index.getBody()).contains("DevPilot");

        var projects = rest.getForEntity("/api/projects", String.class);
        assertThat(projects.getStatusCode()).isEqualTo(HttpStatus.OK);

        var health = rest.getForEntity("/api/executor/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("healthy");
    }
}
