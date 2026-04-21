package org.example.order;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class OrderServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;
        Server server = ServerBuilder.forPort(port)
                .addService(new OrderServiceImpl())
                .build()
                .start();

        System.out.println("OrderServer démarré sur le port " + port);
        server.awaitTermination();
    }
}