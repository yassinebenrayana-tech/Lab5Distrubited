package org.example.kitchen;

import com.gourmet.kitchen.KitchenServiceGrpc;
import com.gourmet.kitchen.RejectRequest;
import com.gourmet.kitchen.RejectResponse;
import com.gourmet.kitchen.TicketRequest;
import com.gourmet.kitchen.TicketResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.kitchen.model.KitchenTicket;
import org.example.kitchen.repository.KitchenTicketRepository;
import org.springframework.context.annotation.Profile;

@GrpcService
@Profile("kitchen")
public class KitchenServiceImpl extends KitchenServiceGrpc.KitchenServiceImplBase {
    private final KitchenTicketRepository kitchenTicketRepository;

    public KitchenServiceImpl(KitchenTicketRepository kitchenTicketRepository) {
        this.kitchenTicketRepository = kitchenTicketRepository;
    }

    @Override
    public void createTicket(TicketRequest request, StreamObserver<TicketResponse> responseObserver) {
        KitchenTicket ticket = kitchenTicketRepository.findById(request.getOrderId()).orElseGet(KitchenTicket::new);
        ticket.setOrderId(request.getOrderId());
        ticket.setStatus("CREATED");
        kitchenTicketRepository.save(ticket);

        System.out.println("[KitchenService] Ticket created for order: " + request.getOrderId());

        TicketResponse response = TicketResponse.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void rejectTicket(RejectRequest request, StreamObserver<RejectResponse> responseObserver) {
        KitchenTicket ticket = kitchenTicketRepository.findById(request.getOrderId()).orElseGet(KitchenTicket::new);
        ticket.setOrderId(request.getOrderId());
        ticket.setStatus("CANCELLED");
        kitchenTicketRepository.save(ticket);

        System.out.println("[KitchenService] COMPENSATION: ticket cancelled for order: " + request.getOrderId());

        RejectResponse response = RejectResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
