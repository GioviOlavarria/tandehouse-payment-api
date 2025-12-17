package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tande.house.paymentapi.dto.*;
import tande.house.paymentapi.model.Payment;
import tande.house.paymentapi.model.PaymentStatus;
import tande.house.paymentapi.repo.PaymentRepository;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final FlowClient flowClient;

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, String urlConfirmation, String urlReturn) {
        String commerceOrder = request.getCommerceOrder();
        String subject = request.getSubject();
        int amount = request.getAmount();


        Map<String, Object> flowResp = flowClient.createPayment(commerceOrder, subject, amount, "email@example.com", urlConfirmation, urlReturn);

        String token = String.valueOf(flowResp.get("token"));


        Payment payment = new Payment();
        payment.setCommerceOrder(commerceOrder);
        payment.setToken(token);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(amount);
        payment.setCreatedAt(OffsetDateTime.now());
        paymentRepo.save(payment);

        return new CreatePaymentResponse(
                String.valueOf(flowResp.get("url")),
                token
        );
    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(String token) {

        Map<String, Object> flowResp = flowClient.getStatus(token);
        String status = String.valueOf(flowResp.get("status"));

        if ("PAID".equals(status)) {
            // Update payment status in DB
            Payment payment = paymentRepo.findByToken(token)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
            payment.setStatus(PaymentStatus.PAID);
            paymentRepo.save(payment);
        }

        return new VerifyPaymentResponse(status, String.valueOf(flowResp.get("commerceOrder")), token);
    }

    @Transactional
    public PaymentStatusResponse getPaymentStatus(String commerceOrder) {
        Payment payment = paymentRepo.findByCommerceOrder(commerceOrder)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        return new PaymentStatusResponse(payment.getStatus().name(), payment.getCommerceOrder(), payment.getAmount(), payment.getToken());
    }
}
