package tande.house.paymentapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tande.house.paymentapi.dto.*;
import tande.house.paymentapi.service.PaymentService;

@RestController
@RequestMapping("/flow")
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "https://tande-house-migrado-i1fv.vercel.app",
                "http://localhost:3000",
                "http://localhost:5173"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.flow.urlConfirmation}")
    private String urlConfirmation;

    @Value("${app.flow.urlReturn}")
    private String urlReturn;

    @PostMapping("/create")
    public CreatePaymentResponse create(@Valid @RequestBody CreatePaymentRequest req) {
        return paymentService.createPayment(req, urlConfirmation, urlReturn);
    }

    @PostMapping("/confirm")
    public VerifyPaymentResponse confirm(@RequestParam("token") String token) {
        return paymentService.verifyPayment(token);
    }


    @GetMapping("/return")
    public VerifyPaymentResponse handleReturn(@RequestParam("token") String token) {
        return paymentService.verifyPayment(token);
    }

    @GetMapping("/status/{commerceOrder}")
    public PaymentStatusResponse status(@PathVariable String commerceOrder) {
        return paymentService.getPaymentStatus(commerceOrder);
    }
}
