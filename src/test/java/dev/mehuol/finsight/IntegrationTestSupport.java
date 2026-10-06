package dev.mehuol.finsight;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that start the whole application, on a random free port. Each run gets its own
 * throwaway pgvector database in Docker (Testcontainers), so tests never touch the development
 * database. No Gemini call happens at startup, so a placeholder key is enough. All subclasses
 * share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-key",
        "spring.ai.google.genai.embedding.api-key=test-key",
        "spring.docker.compose.enabled=false" })
@Import(IntegrationTestSupport.Containers.class)
public abstract class IntegrationTestSupport {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        }
    }
}
