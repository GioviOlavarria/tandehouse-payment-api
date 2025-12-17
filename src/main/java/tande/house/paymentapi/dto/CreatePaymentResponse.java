package tande.house.paymentapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatePaymentResponse {
    private String url;
    private String token;
}
