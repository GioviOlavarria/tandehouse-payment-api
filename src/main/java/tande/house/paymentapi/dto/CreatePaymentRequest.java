package tande.house.paymentapi.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreatePaymentRequest {


    private Long userId;
    private List<CartItem> cart;


    private String commerceOrder;
    private String subject;
    private Integer amount;

    @Email
    private String email;

    @Getter
    @Setter
    public static class CartItem {
        private String productId;
        private Integer quantity;
    }
}
