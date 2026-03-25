package br.fiscalbrain

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.security.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
    ]
)
class FiscalbrainApplicationTests {
    @MockBean
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun contextLoads() {
    }
}
