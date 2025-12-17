package tande.house.paymentapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotBlank
    private String commerceOrder;

    @NotBlank
    private String subject;

    @Min(1)
    private int amount;

    @NotBlank
    @Email
    private String email;
}
