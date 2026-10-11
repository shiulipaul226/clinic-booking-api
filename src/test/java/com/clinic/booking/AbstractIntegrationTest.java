package com.clinic.booking;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared base so every integration test reuses ONE Spring context (and one in-memory H2 database).
 * Each test creates its own uniquely-named data, so tests do not interfere with each other.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
}
