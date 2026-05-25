package com.clearinvest.backend.client;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class YahooFinanceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public String getQuoteSummary(String ticker) {
        String yahooTicker = ticker.endsWith(".SA") ? ticker : ticker + ".SA";
        String url = "https://query2.finance.yahoo.com/v8/finance/chart/"
                + yahooTicker
                + "?interval=1d&range=1d";

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }
}
