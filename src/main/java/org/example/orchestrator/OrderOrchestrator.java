package org.example.orchestrator;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.RejectResponse;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * OrderOrchestrator implements the Saga Pattern for distributed transactions
 * across Order, Kitchen, and Accounting microservices
 */
@Slf4j
@Service
@Profile("orchestrator")
public class OrderOrchestrator {

    private final String orderServiceHost;
    private final int orderServicePort;
    private final String kitchenServiceHost;
    private final int kitchenServicePort;
    private final String accountingServiceHost;
    private final int accountingServicePort;

    private ManagedChannel orderChannel;
    private ManagedChannel kitchenChannel;
    private ManagedChannel accountingChannel;

    private OrderServiceGrpc.OrderServiceBlockingStub orderStub;
    private KitchenServiceGrpc.KitchenServiceBlockingStub kitchenStub;
    private AccountingServiceGrpc.AccountingServiceBlockingStub accountingStub;

    /**
     * Initialize gRPC channels and stubs
     */
    public OrderOrchestrator(
            @Value("${services.order.host:localhost}") String orderServiceHost,
            @Value("${services.order.port:9091}") int orderServicePort,
            @Value("${services.kitchen.host:localhost}") String kitchenServiceHost,
            @Value("${services.kitchen.port:9092}") int kitchenServicePort,
            @Value("${services.accounting.host:localhost}") String accountingServiceHost,
            @Value("${services.accounting.port:9093}") int accountingServicePort
    ) {
        this.orderServiceHost = orderServiceHost;
        this.orderServicePort = orderServicePort;
        this.kitchenServiceHost = kitchenServiceHost;
        this.kitchenServicePort = kitchenServicePort;
        this.accountingServiceHost = accountingServiceHost;
        this.accountingServicePort = accountingServicePort;
        initializeChannels();
    }

    private void initializeChannels() {
        this.orderChannel = ManagedChannelBuilder
                .forAddress(orderServiceHost, orderServicePort)
                .usePlaintext()
                .build();

        this.kitchenChannel = ManagedChannelBuilder
                .forAddress(kitchenServiceHost, kitchenServicePort)
                .usePlaintext()
                .build();

        this.accountingChannel = ManagedChannelBuilder
                .forAddress(accountingServiceHost, accountingServicePort)
                .usePlaintext()
                .build();

        this.orderStub = OrderServiceGrpc.newBlockingStub(orderChannel);
        this.kitchenStub = KitchenServiceGrpc.newBlockingStub(kitchenChannel);
        this.accountingStub = AccountingServiceGrpc.newBlockingStub(accountingChannel);

        log.info("✓ Canaux gRPC initialisés avec succès");
    }

    /**
     * SAGA PATTERN IMPLEMENTATION
     * Orchestrates a distributed transaction across three microservices:
     * 1. OrderService: Create/Update order
     * 2. KitchenService: Create/Cancel kitchen ticket
     * 3. AccountingService: Authorize payment
     *
     * If any step fails, compensation transactions are triggered (Undo)
     */
    public void executeOrderSaga(String orderId, double amount) {
        log.info("\n=====================================================");
        log.info("🚀 DÉBUT DE L'ORCHESTRATION - SAGA PATTERN");
        log.info("Commande ID : {}", orderId);
        log.info("Montant     : {} $", amount);
        log.info("=====================================================");

        try {
            // ÉTAPE 1 : Créer une commande en attente d'approbation (APPROVAL_PENDING)
            log.info("→ [ÉTAPE 1] Order Service - Mise à jour du statut : APPROVAL_PENDING...");
            UpdateStatusResponse statusRes = orderStub.updateStatus(
                    UpdateStatusRequest.newBuilder()
                            .setOrderId(orderId)
                            .setStatus("APPROVAL_PENDING")
                            .build()
            );

            if (!statusRes.getAcknowledged()) {
                throw new RuntimeException("❌ Impossible de mettre à jour le statut de la commande.");
            }
            log.info("✓ Commande créée en attente d'approbation");

            // ÉTAPE 2 : Créer un ticket en cuisine
            log.info("→ [ÉTAPE 2] Kitchen Service - Création du ticket cuisine...");
            TicketResponse ticketRes = kitchenStub.createTicket(
                    TicketRequest.newBuilder()
                            .setOrderId(orderId)
                            .build()
            );

            if (!ticketRes.getSuccess()) {
                throw new RuntimeException("❌ La cuisine a refusé la création du ticket.");
            }
            log.info("✓ Ticket créé en cuisine");

            // ÉTAPE 3 : Vérification des fonds via le service comptable
            log.info("→ [ÉTAPE 3] Accounting Service - Vérification des fonds ({} $)...", amount);
            AuthorizeResponse authRes = accountingStub.authorizeCard(
                    AuthorizeRequest.newBuilder()
                            .setOrderId(orderId)
                            .setAmount(amount)
                            .build()
            );

            // ÉTAPE 4 : Validation finale OU Compensation (SAGA Pattern)
            if (authRes.getAuthorized()) {
                // ✅ SUCCÈS : Approver la commande
                log.info("✓ Paiement autorisé ! (montant < 100$)");
                log.info("→ [ÉTAPE 4] Mise à jour du statut final : APPROVED");

                orderStub.updateStatus(
                        UpdateStatusRequest.newBuilder()
                                .setOrderId(orderId)
                                .setStatus("APPROVED")
                                .build()
                );

                log.info("\n✅ ===== RÉSULTAT FINAL : COMMANDE APPROUVÉE =====");
                log.info("Statut : APPROVED");
                log.info("Paiement : AUTORISÉ\n");

            } else {
                // ❌ ÉCHEC : Lancer les transactions de compensation (UNDO)
                log.warn("❌ Paiement REFUSÉ (montant >= 100$) ! Lancement de la COMPENSATION...");

                // COMPENSATION 1 : Annuler le ticket cuisine
                log.warn("→ [COMPENSATION] Annulation du ticket cuisine...");
                try {
                    RejectResponse rejectRes = kitchenStub.rejectTicket(
                            RejectRequest.newBuilder()
                                    .setOrderId(orderId)
                                    .build()
                    );
                    if (rejectRes.getAcknowledged()) {
                        log.warn("  ✓ Ticket cuisine annulé avec succès");
                    }
                } catch (Exception e) {
                    log.error("  ❌ Erreur lors de l'annulation du ticket cuisine : {}", e.getMessage());
                }

                // COMPENSATION 2 : Marquer la commande comme rejetée
                log.warn("→ [COMPENSATION] Mise à jour du statut : REJECTED");
                try {
                    UpdateStatusResponse rejectStatusRes = orderStub.updateStatus(
                            UpdateStatusRequest.newBuilder()
                                    .setOrderId(orderId)
                                    .setStatus("REJECTED")
                                    .build()
                    );
                    if (rejectStatusRes.getAcknowledged()) {
                        log.warn("  ✓ Commande marquée comme rejetée");
                    }
                } catch (Exception e) {
                    log.error("  ❌ Erreur lors du rejet de la commande : {}", e.getMessage());
                }

                log.info("\n❌ ===== RÉSULTAT FINAL : COMMANDE REJETÉE =====");
                log.info("Statut : REJECTED");
                log.info("Raison : Paiement refusé (montant >= 100$)\n");
            }

        } catch (Exception e) {
            log.error("\n❌ !!! ERREUR CRITIQUE DURANT L'ORCHESTRATION !!!");
            log.error("Détails : {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de l'orchestration du Saga", e);
        }
    }

    /**
     * Shutdown gRPC channels gracefully
     */
    public void shutdown() {
        try {
            if (orderChannel != null) orderChannel.shutdown();
            if (kitchenChannel != null) kitchenChannel.shutdown();
            if (accountingChannel != null) accountingChannel.shutdown();
            log.info("✓ Canaux gRPC fermés avec succès");
        } catch (Exception e) {
            log.error("Erreur lors de la fermeture des canaux : {}", e.getMessage());
        }
    }
}
