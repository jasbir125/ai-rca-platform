package com.airca.orderservice.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank String itemDescription,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
