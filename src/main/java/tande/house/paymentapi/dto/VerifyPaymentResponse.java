package tande.house.paymentapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VerifyPaymentResponse {
    private String status;
    private String commerceOrder;
    private String token;
}
