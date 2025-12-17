package tande.house.paymentapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePaymentRequest {

    @NotBlank
    private String commerceOrder;

    @NotBlank
    private String subject;

    private int amount;
}
