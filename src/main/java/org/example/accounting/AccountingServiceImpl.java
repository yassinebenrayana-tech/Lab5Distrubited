package org.example.accounting;

import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.stub.StreamObserver;

public class AccountingServiceImpl extends AccountingServiceGrpc.AccountingServiceImplBase {
    @Override
    public void authorizeCard(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
        double amount = request.getAmount();
        boolean isAuthorized = amount < 100.0;

        System.out.println("[AccountingService] Demande d'autorisation pour la commande " + request.getOrderId() + " d'un montant de " + amount);
        System.out.println("[AccountingService] Résultat de l'autorisation : " + (isAuthorized ? "ACCEPTEE" : "REFUSEE"));

        AuthorizeResponse response = AuthorizeResponse.newBuilder()
                .setAuthorized(isAuthorized)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}