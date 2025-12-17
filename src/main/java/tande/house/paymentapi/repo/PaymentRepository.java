package tande.house.paymentapi.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tande.house.paymentapi.model.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByCommerceOrder(String commerceOrder);
    Optional<Payment> findByToken(String token);
}
