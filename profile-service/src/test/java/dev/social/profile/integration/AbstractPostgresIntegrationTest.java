package dev.social.profile.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need the real thing: a real PostgreSQL 17, the real
 * Flyway migrations, and therefore the real PL/pgSQL procedures.
 *
 * <p>H2 would be faster and useless here - it has no {@code CREATE PROCEDURE
 * ... LANGUAGE plpgsql}, no {@code FOR UPDATE} semantics worth testing, and no
 * SQLSTATE {@code P0001}. Everything interesting about this schema is
 * PostgreSQL-specific, so the test runs against PostgreSQL.
 *
 * <p>The container is started once in a static initialiser and deliberately
 * never stopped, rather than being managed per-class by {@code @Container}.
 * Testcontainers' Ryuk sidecar reaps it when the JVM exits, and the whole
 * module's tests share a single startup instead of paying ~2s per test class.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("profiledb")
                    .withUsername("profile_service")
                    .withPassword("profile_service_test_pwd")
                    .withUrlParam("escapeSyntaxCallMode", "call")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.seeder.enabled", () -> false);
    }
}
