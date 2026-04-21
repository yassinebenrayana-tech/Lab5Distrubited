package org.example.orchestrator;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class OrderOrchestrator {

    public static void main(String[] args) {
        // --- INITIALISATION MANUELLE DES PARAMÈTRES ---
        String orderId = "ORDER_2024_001"; // Votre ID de commande ici
        double amount = 75.50;            // Votre montant ici
        // ----------------------------------------------

        System.out.println("\n=====================================================");
        System.out.println("DEBUT DE L'ORCHESTRATION");
        System.out.println("Commande ID : " + orderId);
        System.out.println("Montant     : " + amount + " $");
        System.out.println("=====================================================\n");

        // Connexion aux différents microservices gRPC
        ManagedChannel orderChannel = ManagedChannelBuilder.forTarget("localhost:50051")
                .usePlaintext()
                .build();
        ManagedChannel kitchenChannel = ManagedChannelBuilder.forTarget("localhost:50052")
                .usePlaintext()
                .build();
        ManagedChannel accountingChannel = ManagedChannelBuilder.forTarget("localhost:50053")
                .usePlaintext()
                .build();

        // Création des clients (Stubs)
        OrderServiceGrpc.OrderServiceBlockingStub orderStub = OrderServiceGrpc.newBlockingStub(orderChannel);
        KitchenServiceGrpc.KitchenServiceBlockingStub kitchenStub = KitchenServiceGrpc.newBlockingStub(kitchenChannel);
        AccountingServiceGrpc.AccountingServiceBlockingStub accountingStub = AccountingServiceGrpc.newBlockingStub(accountingChannel);

        try {
            // Étape 1 : Passer la commande en attente d'approbation (APPROVAL_PENDING)
            System.out.println("-> [Order Service] Mise à jour du statut : APPROVAL_PENDING...");
            var statusRes = orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                    .setOrderId(orderId)
                    .setStatus("APPROVAL_PENDING")
                    .build());

            if (!statusRes.getAcknowledged()) {
                throw new RuntimeException("Impossible de mettre à jour le statut de la commande.");
            }

            // Étape 2 : Créer le ticket en cuisine
            System.out.println("-> [Kitchen Service] Création du ticket cuisine...");
            TicketResponse ticketRes = kitchenStub.createTicket(TicketRequest.newBuilder()
                    .setOrderId(orderId)
                    .build());

            if (!ticketRes.getSuccess()) {
                throw new RuntimeException("La cuisine a refusé la création du ticket.");
            }

            // Étape 3 : Autorisation de la carte bancaire via le service comptable
            System.out.println("-> [Accounting Service] Vérification des fonds (" + amount + "$)...");
            AuthorizeResponse authRes = accountingStub.authorizeCard(AuthorizeRequest.newBuilder()
                    .setOrderId(orderId)
                    .setAmount(amount)
                    .build());

            // Étape 4 : Validation finale ou Compensation (Saga Pattern)
            if (authRes.getAuthorized()) {
                // SUCCÈS : On approuve la commande
                System.out.println("-> [Success] Paiement autorisé !");
                orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                        .setOrderId(orderId)
                        .setStatus("APPROVED")
                        .build());
                System.out.println("\n### RÉSULTAT FINAL : COMMANDE APPROUVÉE ###");
            } else {
                // ÉCHEC : On lance les transactions de compensation (Undo)
                System.out.println("-> [Failure] Paiement refusé. Lancement de la compensation...");

                // Annulation en cuisine
                kitchenStub.rejectTicket(RejectRequest.newBuilder()
                        .setOrderId(orderId)
                        .build());
                System.out.println("   [Undo] Ticket cuisine annulé.");

                // Annulation de la commande
                orderStub.updateStatus(UpdateStatusRequest.newBuilder()
                        .setOrderId(orderId)
                        .setStatus("REJECTED")
                        .build());
                System.out.println("   [Undo] Commande marquée comme rejetée.");

                System.out.println("\n### RÉSULTAT FINAL : COMMANDE REJETÉE ###");
            }

        } catch (Exception e) {
            System.err.println("\n!!! ERREUR CRITIQUE DURANT L'ORCHESTRATION !!!");
            System.err.println("Détails : " + e.getMessage());
        } finally {
            // Fermeture propre des connexions
            orderChannel.shutdown();
            kitchenChannel.shutdown();
            accountingChannel.shutdown();
            System.out.println("\nConnexions gRPC fermées.");
        }
    }
}