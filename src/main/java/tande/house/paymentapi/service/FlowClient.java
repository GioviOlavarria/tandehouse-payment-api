package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class FlowClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.flow.apiUrl}")
    private String apiUrl;

    @Value("${app.flow.apiKey}")
    private String apiKey;

    @Value("${app.flow.secretKey}")
    private String secretKey;

    public Map<String, Object> createPayment(String commerceOrder, String subject, int amount, String urlConfirmation, String urlReturn) {
        String endpoint = apiUrl + "/payment/create";

        Map<String, String> params = new HashMap<>();
        params.put("apiKey", apiKey);
        params.put("commerceOrder", commerceOrder);
        params.put("subject", subject);
        params.put("amount", String.valueOf(amount));
        params.put("currency", "CLP");
        params.put("urlConfirmation", urlConfirmation);
        params.put("urlReturn", urlReturn);

        String signature = sign(params);
        params.put("s", signature);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(endpoint, HttpMethod.POST, entity, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Flow createPayment failed. HTTP " + resp.getStatusCode());
        }
        return (Map<String, Object>) resp.getBody();
    }

    public Map<String, Object> getStatus(String token) {
        String endpoint = apiUrl + "/payment/getStatus";

        Map<String, String> params = new HashMap<>();
        params.put("apiKey", apiKey);
        params.put("token", token);

        String signature = sign(params);
        params.put("s", signature);

        String url = endpoint + "?" + toQuery(params);

        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Flow getStatus failed. HTTP " + resp.getStatusCode());
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
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
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
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error signing Flow request", e);
        }
    }
}