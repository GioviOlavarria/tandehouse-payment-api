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

    private final FlowClient flowClient;
    private final PaymentRepository paymentRepo;

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest req, String urlConfirmation, String urlReturn) {

        Payment payment = paymentRepo.findByCommerceOrder(req.getCommerceOrder()).orElse(null);
        if (payment == null) {
            payment = new Payment();
            payment.setCommerceOrder(req.getCommerceOrder());
            payment.setAmount(req.getAmount());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setCreatedAt(OffsetDateTime.now());
        } else {
            payment.setAmount(req.getAmount());
            payment.setStatus(PaymentStatus.PENDING);
        }

        Map<String, Object> flowResp = flowClient.createPayment(
                req.getCommerceOrder(),
                req.getSubject(),
                req.getAmount(),
                req.getEmail(),
                urlConfirmation,
                urlReturn
        );

        String url = String.valueOf(flowResp.get("url"));
        String token = String.valueOf(flowResp.get("token"));

        payment.setToken(token);
        paymentRepo.save(payment);

        String redirectUrl = url + "?token=" + token;
        return new CreatePaymentResponse(redirectUrl, token);
    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(String token) {
        Map<String, Object> flowResp = flowClient.getStatus(token);

        String statusRaw = String.valueOf(flowResp.get("status"));
        String commerceOrder = String.valueOf(flowResp.get("commerceOrder"));

        Payment payment = paymentRepo.findByToken(token).orElse(null);
        if (payment == null && commerceOrder != null && !commerceOrder.isBlank()) {
            payment = paymentRepo.findByCommerceOrder(commerceOrder).orElse(null);
        }
        if (payment == null) {
            payment = new Payment();
            payment.setCommerceOrder(commerceOrder);
            payment.setAmount(0);
            payment.setToken(token);
            payment.setCreatedAt(OffsetDateTime.now());
        }

        PaymentStatus newStatus;
        if ("2".equals(statusRaw) || "PAID".equalsIgnoreCase(statusRaw)) {
            newStatus = PaymentStatus.PAID;
        } else if ("3".equals(statusRaw) || "4".equals(statusRaw) || "FAILED".equalsIgnoreCase(statusRaw)) {
            newStatus = PaymentStatus.FAILED;
        } else {
            newStatus = PaymentStatus.PENDING;
        }

        payment.setStatus(newStatus);
        paymentRepo.save(payment);

        return new VerifyPaymentResponse(newStatus.name(), payment.getCommerceOrder(), token);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String commerceOrder) {
        Payment payment = paymentRepo.findByCommerceOrder(commerceOrder)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        return new PaymentStatusResponse(
                payment.getStatus().name(),
                payment.getCommerceOrder(),
                payment.getAmount(),
                payment.getToken()
        );
    }
}
