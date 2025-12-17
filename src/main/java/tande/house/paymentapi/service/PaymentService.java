package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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

    private final RestTemplate http = new RestTemplate();

    @Value("${services.product}")
    private String productServiceUrl;

    @Value("${internal.serviceKey}")
    private String internalServiceKey;

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest req, String urlConfirmation, String urlReturn) {

        int amount = calculateTotalAmount(req);

        String commerceOrder = "TH-" + req.getUserId() + "-" + System.currentTimeMillis();
        String subject = "Compra TandeHouse " + commerceOrder;

        Payment payment = new Payment();
        payment.setCommerceOrder(commerceOrder);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(OffsetDateTime.now());

        try {
            Map<String, Object> flowResp = flowClient.createPayment(
                    commerceOrder,
                    subject,
                    amount,
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

        } catch (HttpClientErrorException e) {

            throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al comunicarse con Flow", e);
        }
    }

    private int calculateTotalAmount(CreatePaymentRequest req) {
        int total = 0;

        for (CreatePaymentRequest.CartItem item : req.getCart()) {
            long pid = normalizeProductIdToLong(item.getProductId());

            int price = fetchProductPrice(pid);
            total += price * item.getQuantity();
        }

        if (total <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total inválido");
        return total;
    }

    private long normalizeProductIdToLong(String raw) {
        if (raw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId vacío");


        Matcher m = DIGITS.matcher(raw);
        if (!m.find()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId inválido: " + raw);
        return Long.parseLong(m.group(1));
    }

    private int fetchProductPrice(long productId) {
        String url = productServiceUrl + "/products/" + productId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalServiceKey);

        ResponseEntity<Map> resp = http.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no encontrado: " + productId);
        }

        Object precio = resp.getBody().get("precio");
        if (precio == null) precio = resp.getBody().get("price");
        if (precio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto sin precio: " + productId);
        }

        if (precio instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(precio));
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
}
