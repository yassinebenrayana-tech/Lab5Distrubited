package org.example.order;

import com.gourmet.order.OrderServiceGrpc;
import com.gourmet.order.UpdateStatusRequest;
import com.gourmet.order.UpdateStatusResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.order.model.Order;
import org.example.order.repository.OrderRepository;
import org.springframework.context.annotation.Profile;

@GrpcService
@Profile("order")
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void updateStatus(UpdateStatusRequest request, StreamObserver<UpdateStatusResponse> responseObserver) {
        Order order = orderRepository.findById(request.getOrderId()).orElseGet(Order::new);
        order.setId(request.getOrderId());
        order.setStatus(request.getStatus());
        orderRepository.save(order);

        System.out.println("[OrderService] Order " + request.getOrderId() + " saved with status: " + request.getStatus());

        UpdateStatusResponse response = UpdateStatusResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
