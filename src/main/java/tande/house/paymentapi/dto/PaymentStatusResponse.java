package tande.house.paymentapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentStatusResponse {
    private String status;
    private String commerceOrder;
    private int amount;
    private String token;
}
