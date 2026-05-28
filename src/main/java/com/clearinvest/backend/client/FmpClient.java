package com.clearinvest.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FmpClient {

    @Value("${fmp.token}")
    private String token;

    @Value("${fmp.url}")
    private String fmpUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getIncomeStatement(String ticker) {
        String url = fmpUrl + "/income-statement/" + ticker + "?limit=1&apikey=" + token;
        return restTemplate.getForObject(url, String.class);
    }

    public String getBalanceSheet(String ticker) {
        String url = fmpUrl + "/balance-sheet-statement/" + ticker + "?limit=1&apikey=" + token;
        return restTemplate.getForObject(url, String.class);
    }

    public String getCashFlow(String ticker) {
        String url = fmpUrl + "/cash-flow-statement/" + ticker + "?limit=1&apikey=" + token;
        return restTemplate.getForObject(url, String.class);
    }

    public String getKeyMetrics(String ticker) {
        String url = fmpUrl + "/key-metrics/" + ticker + "?limit=1&apikey=" + token;
        return restTemplate.getForObject(url, String.class);
    }
}
