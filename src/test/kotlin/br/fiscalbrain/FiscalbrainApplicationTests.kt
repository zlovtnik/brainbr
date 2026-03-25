package br.fiscalbrain

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
    ]
)
class FiscalbrainApplicationTests {
    @Test
    fun contextLoads() {
    }
}
