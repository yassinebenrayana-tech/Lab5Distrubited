package org.example.order;

// 1. Notice these are all "com.gourmet", NOT "com.example"
import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import io.grpc.stub.StreamObserver;

public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    @Override
    public void updateStatus(UpdateStatusRequest request, StreamObserver<UpdateStatusResponse> responseObserver) {
        System.out.println("[OrderService] Mise à jour de la commande " + request.getOrderId() + " au statut : " + request.getStatus());

        UpdateStatusResponse response = UpdateStatusResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}