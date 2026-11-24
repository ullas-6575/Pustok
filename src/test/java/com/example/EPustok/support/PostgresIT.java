package com.example.EPustok.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests that need a throwaway PostgreSQL. The container is started once per test class,
 * Flyway runs every migration against it, and the "test" profile stops application.properties from reading .env.
 * The JWT_SECRET below is a random value generated for tests only; it is not a credential.
 */
@SpringBootTest(properties = "JWT_SECRET=NcGBobrtXXMal+63hf+B9s0jaGbJ4JTyeT1+ZKP4mwc=")
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
}
