package org.example.kitchen;

import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.RejectResponse;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import io.grpc.stub.StreamObserver;

public class KitchenServiceImpl extends KitchenServiceGrpc.KitchenServiceImplBase {
    @Override
    public void createTicket(TicketRequest request, StreamObserver<TicketResponse> responseObserver) {
        System.out.println("[KitchenService] Création du ticket pour la commande : " + request.getOrderId());

        TicketResponse response = TicketResponse.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void rejectTicket(RejectRequest request, StreamObserver<RejectResponse> responseObserver) {
        System.out.println("[KitchenService] COMPENSATION : Annulation du ticket pour la commande : " + request.getOrderId());

        RejectResponse response = RejectResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}