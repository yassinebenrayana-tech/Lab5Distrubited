package org.example.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaResponse {
    private String orderId;
    private double amount;
    private boolean success;
    private String message;
}

