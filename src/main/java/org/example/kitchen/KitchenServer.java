package org.example.kitchen;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class KitchenServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50052;
        Server server = ServerBuilder.forPort(port)
                .addService(new KitchenServiceImpl())
                .build()
                .start();

        System.out.println("KitchenServer démarré sur le port " + port);
        server.awaitTermination();
    }
}