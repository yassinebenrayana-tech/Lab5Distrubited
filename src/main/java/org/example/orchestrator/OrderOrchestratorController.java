package org.example.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.example.order.repository.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@Profile("orchestrator")
public class OrderOrchestratorController {
    private final OrderOrchestrator orderOrchestrator;
    private final OrderRepository orderRepository;

    public OrderOrchestratorController(OrderOrchestrator orderOrchestrator, OrderRepository orderRepository) {
        this.orderOrchestrator = orderOrchestrator;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/execute-saga")
    public ResponseEntity<SagaResponse> executeSaga(
            @RequestParam String orderId,
            @RequestParam double amount
    ) {
        try {
            log.info("Executing saga for orderId={}, amount={}", orderId, amount);
            orderOrchestrator.executeOrderSaga(orderId, amount);

            SagaResponse response = SagaResponse.builder()
                    .orderId(orderId)
                    .amount(amount)
                    .success(true)
                    .message("Saga executed successfully")
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Saga execution failed", e);

            SagaResponse response = SagaResponse.builder()
                    .orderId(orderId)
                    .amount(amount)
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .<ResponseEntity<Map<String, Object>>>map(order -> ResponseEntity.ok(Map.of(
                        "orderId", order.getId(),
                        "status", order.getStatus()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "orderId", orderId,
                        "status", "NOT_FOUND"
                )));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Orchestrator Service is running");
    }
}
