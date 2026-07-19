package com.hadilao.be;

import com.hadilao.be.modules.auth.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class HadilaoBackendApplicationTests {

    @MockBean
    private MailService mailService;

    @Test
    void contextLoads() {
    }

}
