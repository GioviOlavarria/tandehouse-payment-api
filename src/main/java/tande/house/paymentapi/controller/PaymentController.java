package tande.house.paymentapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tande.house.paymentapi.dto.*;
import tande.house.paymentapi.service.PaymentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${internal.serviceKey}")
    private String internalKey;

    private void checkInternalKey(String key) {
        if (internalKey != null && !internalKey.isBlank()) {
            if (key == null || !internalKey.equals(key))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping("/create")
    public CreatePaymentResponse createPayment(
            @RequestHeader("X-Internal-Key") String key,
            @RequestBody CreatePaymentRequest request,
            @RequestParam("urlConfirmation") String urlConfirmation,
            @RequestParam("urlReturn") String urlReturn
    ) {
        checkInternalKey(key);
        return paymentService.createPayment(request, urlConfirmation, urlReturn);
    }

    @PostMapping("/verify")
    public VerifyPaymentResponse verifyPayment(@RequestParam("token") String token) {
        return paymentService.verifyPayment(token);
    }

    @GetMapping("/status/{commerceOrder}")
    public PaymentStatusResponse getPaymentStatus(@PathVariable String commerceOrder) {
        return paymentService.getPaymentStatus(commerceOrder);
    }
}
