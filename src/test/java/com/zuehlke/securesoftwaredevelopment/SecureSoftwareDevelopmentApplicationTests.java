package com.zuehlke.securesoftwaredevelopment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
class SecureSoftwareDevelopmentApplicationTests {

    @Test
    void contextLoads() {
    }

}
