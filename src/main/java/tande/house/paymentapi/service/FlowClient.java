package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    public Map<String, Object> createPayment(
            String commerceOrder,
            String subject,
            int amount,
            String email,
            String urlConfirmation,
            String urlReturn
    ) {
        // ✅ mutable
        Map<String, String> params = new HashMap<>();
        params.put("apiKey", apiKey);
        params.put("commerceOrder", commerceOrder);
        params.put("subject", subject);
        params.put("currency", "CLP");
        params.put("amount", String.valueOf(amount));
        params.put("email", email);
        params.put("urlConfirmation", urlConfirmation);
        params.put("urlReturn", urlReturn);

        params.put("s", sign(params));

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
        Map<String, String> params = new HashMap<>();
        params.put("apiKey", apiKey);
        params.put("token", token);
        params.put("s", sign(params));

        String url = baseUrl + "/payment/getStatus?" + toQuery(params);

        ResponseEntity<Map> resp = http.getForEntity(url, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Flow getStatus error: " + resp.getStatusCode());
        }
        return (Map<String, Object>) resp.getBody();
    }

    private String toQuery(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = params.get(k);
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8))
                    .append("&");
        }
        if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private String sign(Map<String, String> params) {

        List<String> keys = new ArrayList<>(params.keySet());
        keys.remove("s");
        Collections.sort(keys);

        StringBuilder toSign = new StringBuilder();
        for (String k : keys) {
            toSign.append(k).append(params.get(k) == null ? "" : params.get(k));
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error signing Flow request", e);
        }
    }
}
