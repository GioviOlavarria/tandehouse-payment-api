package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tande.house.paymentapi.dto.CreatePaymentRequest;
import tande.house.paymentapi.dto.CreatePaymentResponse;
import tande.house.paymentapi.dto.PaymentStatusResponse;
import tande.house.paymentapi.dto.VerifyPaymentResponse;
import tande.house.paymentapi.model.Payment;
import tande.house.paymentapi.model.PaymentStatus;
import tande.house.paymentapi.repo.PaymentRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final FlowClient flowClient;
    private final PaymentRepository paymentRepo;
    private final RestTemplate restTemplate;

    @Value("${flow.baseUrl}")
    private String flowBaseUrl;

    @Value("${services.billing}")
    private String billingServiceUrl;

    @Value("${internal.serviceKey}")
    private String internalServiceKey;

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest req, String urlConfirmation, String urlReturn) {

        String email = req.getEmail() == null ? null : req.getEmail().trim();

        if (email != null && !email.isBlank()) {
            email = email.toLowerCase().trim();

            String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
            if (!email.matches(emailRegex)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Email con formato inválido");
            }
        }

        if (req.getCommerceOrder() != null && req.getSubject() != null && req.getAmount() != null) {
            String commerceOrder = req.getCommerceOrder().trim();
            String subject = req.getSubject().trim();
            int amount = req.getAmount();

            if (commerceOrder.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "commerceOrder no puede estar vacío");
            }
            if (subject.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "subject no puede estar vacío");
            }
            if (amount <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "amount debe ser mayor a 0");
            }

            return createFlow(commerceOrder, subject, amount, email, urlConfirmation, urlReturn);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Body inválido. Enviar (commerceOrder, subject, amount, email)."
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
        if (urlConfirmation == null || urlConfirmation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "URL de confirmación no configurada");
        }
        if (urlReturn == null || urlReturn.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "URL de retorno no configurada");
        }

        try {
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

            Object urlObj = flowResp.get("url");
            Object tokenObj = flowResp.get("token");

            if (urlObj == null || tokenObj == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Flow no devolvió url o token en la respuesta"
                );
            }

            String url = String.valueOf(urlObj);
            String token = String.valueOf(tokenObj);

            if (url.equals("null") || token.equals("null")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Flow devolvió url o token nulos"
                );
            }

            payment.setToken(token);
            paymentRepo.save(payment);

            String redirectUrl = url + "?token=" + token;

            return new CreatePaymentResponse(redirectUrl, token);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al crear pago: " + e.getMessage()
            );
        }
    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(String token) {
        System.out.println("=== VERIFICANDO PAGO ===");
        System.out.println("Token recibido: " + token);

        Map<String, Object> flowResp = flowClient.getStatus(token);
        System.out.println("Respuesta de Flow: " + flowResp);

        String statusRaw = String.valueOf(flowResp.get("status"));
        String commerceOrder = String.valueOf(flowResp.get("commerceOrder"));

        System.out.println("Status raw: " + statusRaw);
        System.out.println("Commerce Order: " + commerceOrder);

        Payment payment = paymentRepo.findByToken(token).orElse(null);
        if (payment == null && commerceOrder != null && !commerceOrder.isBlank() && !commerceOrder.equals("null")) {
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

        System.out.println("Nuevo status: " + newStatus);

        payment.setStatus(newStatus);
        paymentRepo.save(payment);

        if (newStatus == PaymentStatus.PAID) {
            System.out.println("Pago exitoso, creando boleta...");
            createBoletaIfNotExists(commerceOrder, payment.getAmount());
        }

        return new VerifyPaymentResponse(newStatus.name(), payment.getCommerceOrder(), token);
    }

    private void createBoletaIfNotExists(String commerceOrder, int amount) {
        System.out.println("=== INTENTANDO CREAR BOLETA ===");
        System.out.println("Commerce Order: " + commerceOrder);
        System.out.println("Amount: " + amount);
        System.out.println("Billing Service URL: " + billingServiceUrl);
        System.out.println("Internal Service Key: " + (internalServiceKey != null ? "configurado" : "NO CONFIGURADO"));

        try {
            if (billingServiceUrl == null || billingServiceUrl.isBlank()) {
                System.err.println("ERROR: BILLING_SERVICE_URL no configurada");
                return;
            }

            if (internalServiceKey == null || internalServiceKey.isBlank()) {
                System.err.println("ERROR: INTERNAL_SERVICE_KEY no configurada");
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Key", internalServiceKey);

            Map<String, Object> body = new HashMap<>();
            body.put("commerceOrder", commerceOrder);
            body.put("total", amount);

            System.out.println("Body a enviar: " + body);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = billingServiceUrl + "/boletas/fromCommerceOrder";
            System.out.println("Llamando a: " + url);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("BOLETA CREADA EXITOSAMENTE");
            } else {
                System.err.println("ERROR creando boleta: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("EXCEPCION al crear boleta: " + e.getMessage());
            e.printStackTrace();
        }
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