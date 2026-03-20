package com.wms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WmsApplicationTests {

    @Test
    void contextLoads() {
        // Spring コンテキストが正常にロードされることを検証
    }
}
