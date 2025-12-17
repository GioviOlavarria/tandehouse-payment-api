package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FlowClient {

    private final RestTemplate http = new RestTemplate();

    @Value("${flow.apiKey}")
    private String apiKey;

    @Value("${flow.secretKey}")
    private String secretKey;

    @Value("${flow.baseUrl}")
    private String baseUrl;

    public Map<String, Object> createPayment(String commerceOrder, String subject, int amount, String email,
                                             String urlConfirmation, String urlReturn) {

        Map<String, String> params = Map.of(
                "apiKey", apiKey,
                "commerceOrder", commerceOrder,
                "subject", subject,
                "currency", "CLP",
                "amount", String.valueOf(amount),
                "email", email,
                "urlConfirmation", urlConfirmation,
                "urlReturn", urlReturn
        );

        String signature = sign(params);
        params.put("s", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);

        ResponseEntity<Map> resp = http.exchange(
                baseUrl + "/payment/create",
                HttpMethod.POST,
                req,
                Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Flow create error: " + resp.getStatusCode());
        }
        return (Map<String, Object>) resp.getBody();
    }

    public Map<String, Object> getStatus(String token) {
        Map<String, String> params = Map.of(
                "apiKey", apiKey,
                "token", token,
                "s", sign(Map.of("apiKey", apiKey, "token", token))
        );

        String url = baseUrl + "/payment/getStatus?" + toQuery(params);

        ResponseEntity<Map> resp = http.getForEntity(url, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Flow getStatus error: " + resp.getStatusCode());
        }
        return (Map<String, Object>) resp.getBody();
    }

    private String toQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
        return sb.toString();
    }

    private String sign(Map<String, String> params) {
        // Implement HMAC SHA256 signing as per Flow's specifications
        return "some-signature";  // Placeholder
    }
}
