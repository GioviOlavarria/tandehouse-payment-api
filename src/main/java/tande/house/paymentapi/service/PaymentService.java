package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tande.house.paymentapi.dto.CreatePaymentRequest;
import tande.house.paymentapi.dto.CreatePaymentResponse;
import tande.house.paymentapi.dto.PaymentStatusResponse;
import tande.house.paymentapi.dto.VerifyPaymentResponse;
import tande.house.paymentapi.model.Payment;
import tande.house.paymentapi.model.PaymentStatus;
import tande.house.paymentapi.repo.PaymentRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final FlowClient flowClient;
    private final PaymentRepository paymentRepo;

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest req, String urlConfirmation, String urlReturn) {


        String email = req.getEmail() == null ? null : req.getEmail().trim();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email es requerido");
        }
        // --- MODO 1: viene “flow-like” (commerceOrder/subject/amount)
        if (req.getCommerceOrder() != null && req.getSubject() != null && req.getAmount() != null) {

            String commerceOrder = req.getCommerceOrder();
            String subject = req.getSubject();
            int amount = req.getAmount();

            if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount inválido");

            return createFlow(commerceOrder, subject, amount, email, urlConfirmation, urlReturn);
        }


        if (req.getUserId() != null && req.getCart() != null && !req.getCart().isEmpty()) {


            int amount = 0;
            for (CreatePaymentRequest.CartItem it : req.getCart()) {
                if (it == null || it.getQuantity() == null || it.getQuantity() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity inválida");


            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El backend recibió cart pero no puede calcular amount sin consultar Product Service. Envíame amount o conectamos Product Service."
            );
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Body inválido. Enviar (commerceOrder, subject, amount, email) o (userId, cart[], email)."
        );
    }

    private CreatePaymentResponse createFlow(
            String commerceOrder,
            String subject,
            int amount,
            String email,
            String urlConfirmation,
            String urlReturn
    ) {
        Payment payment = paymentRepo.findByCommerceOrder(commerceOrder).orElse(null);
        if (payment == null) {
            payment = new Payment();
            payment.setCommerceOrder(commerceOrder);
            payment.setCreatedAt(OffsetDateTime.now());
        }
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);

        Map<String, Object> flowResp = flowClient.createPayment(
                commerceOrder,
                subject,
                amount,
                email,
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
        if ("2".equals(statusRaw) || "PAID".equalsIgnoreCase(statusRaw)) newStatus = PaymentStatus.PAID;
        else if ("3".equals(statusRaw) || "4".equals(statusRaw) || "FAILED".equalsIgnoreCase(statusRaw)) newStatus = PaymentStatus.FAILED;
        else newStatus = PaymentStatus.PENDING;

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

    private long normalizeProductIdToLong(String raw) {
        if (raw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId vacío");
        Matcher m = DIGITS.matcher(raw);
        if (!m.find()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId inválido: " + raw);
        return Long.parseLong(m.group(1));
    }
}
