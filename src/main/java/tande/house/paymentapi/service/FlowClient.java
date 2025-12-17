package tande.house.paymentapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", apiKey);
        params.put("commerceOrder", commerceOrder);
        params.put("subject", subject);
        params.put("currency", "CLP");
        params.put("amount", String.valueOf(amount));
        params.put("email", email);
        params.put("urlConfirmation", urlConfirmation);
        params.put("urlReturn", urlReturn);


        String signature = sign(params);
        params.put("s", signature);

        System.out.println("=== DEBUG FLOW CREATE ===");
        System.out.println("Params: " + params);
        System.out.println("Signature: " + signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> resp = http.exchange(
                    baseUrl + "/payment/create",
                    HttpMethod.POST,
                    req,
                    Map.class
            );

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Flow create error: " + resp.getStatusCode());
            }

            return (Map<String, Object>) resp.getBody();

        } catch (HttpStatusCodeException e) {
            System.err.println("FLOW ERROR STATUS: " + e.getStatusCode());
            System.err.println("FLOW ERROR BODY: " + e.getResponseBodyAsString());
            throw new RuntimeException(
                    "FLOW ERROR: " + e.getStatusCode() + " - " + e.getResponseBodyAsString()
            );
        }
    }

    public Map<String, Object> getStatus(String token) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", apiKey);
        params.put("token", token);

        String signature = sign(params);
        params.put("s", signature);

        String url = baseUrl + "/payment/getStatus?" + toQuery(params);

        System.out.println("=== DEBUG FLOW GET STATUS ===");
        System.out.println("URL: " + url);

        try {
            ResponseEntity<Map> resp = http.getForEntity(url, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Flow getStatus error: " + resp.getStatusCode());
            }
            return (Map<String, Object>) resp.getBody();
        } catch (HttpStatusCodeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Flow error: " + e.getStatusCode() + " body=" + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    private String toQuery(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = params.get(k);
            if (v != null && !v.isEmpty()) {
                sb.append(urlEncode(k)).append("=").append(urlEncode(v)).append("&");
            }
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }


    private String sign(Map<String, String> params) {

        List<String> keys = new ArrayList<>(params.keySet());
        keys.remove("s");
        Collections.sort(keys);


        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            String v = params.get(k);

            if (v != null && !v.isEmpty()) {
                canonical.append(k).append(v);
            }
        }

        String dataToSign = canonical.toString();
        System.out.println("=== SIGNATURE DEBUG ===");
        System.out.println("Data to sign: " + dataToSign);
        System.out.println("Secret key: " + secretKey);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] rawHmac = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            // Convertir a hexadecimal
            StringBuilder hex = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                hex.append(String.format("%02x", b));
            }

            String signature = hex.toString();
            System.out.println("Signature generada: " + signature);

            return signature;

        } catch (Exception e) {
            System.err.println("ERROR GENERANDO FIRMA: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error signing Flow request", e);
        }
    }


    private String urlEncode(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}