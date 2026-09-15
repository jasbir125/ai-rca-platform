package com.airca.paymentservice.payment;

import com.airca.paymentservice.chaos.ChaosState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChaosState chaosState;

    @Test
    void returns201OnSuccessfulPayment() throws Exception {
        chaosState.setFailureRate(0.0);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-42\",\"amount\":19.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-42"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void returns500WithStructuredErrorWhenPaymentDeclined() throws Exception {
        chaosState.setFailureRate(1.0);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-43\",\"amount\":19.99}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorType").value("PAYMENT_PROCESSING_FAILED"));

        chaosState.setFailureRate(0.0);
    }

    @Test
    void returns400OnInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value("VALIDATION_FAILED"));
    }
}
