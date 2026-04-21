package org.example.accounting;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class AccountingServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50053;
        Server server = ServerBuilder.forPort(port)
                .addService(new AccountingServiceImpl())
                .build()
                .start();

        System.out.println("AccountingServer démarré sur le port " + port);
        server.awaitTermination();
    }
}