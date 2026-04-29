package org.example.accounting;

import com.gourmet.accounting.AccountingServiceGrpc;
import com.gourmet.accounting.AuthorizeRequest;
import com.gourmet.accounting.AuthorizeResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.accounting.model.Invoice;
import org.example.accounting.repository.InvoiceRepository;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

@GrpcService
@Profile("accounting")
public class AccountingServiceImpl extends AccountingServiceGrpc.AccountingServiceImplBase {
    private final InvoiceRepository invoiceRepository;

    public AccountingServiceImpl(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public void authorizeCard(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
        double amount = request.getAmount();
        boolean authorized = amount < 100.0;

        Invoice invoice = new Invoice();
        invoice.setOrderId(request.getOrderId());
        invoice.setAmount(BigDecimal.valueOf(amount));
        invoice.setStatus(authorized ? "AUTHORIZED" : "REJECTED");
        invoiceRepository.save(invoice);

        System.out.println("[AccountingService] Authorization for order " + request.getOrderId() + ", amount: " + amount);
        System.out.println("[AccountingService] Result: " + (authorized ? "ACCEPTED" : "REJECTED"));

        AuthorizeResponse response = AuthorizeResponse.newBuilder()
                .setAuthorized(authorized)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
