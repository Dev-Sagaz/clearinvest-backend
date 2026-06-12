package com.clearinvest.backend.client;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BinanceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://api.binance.com/api/v3";

    public JSONArray getKlines(String symbol, String interval, int limit) {
        String pair = symbol.toUpperCase() + "USDT";
        String url = BASE_URL + "/klines?symbol=" + pair + "&interval=" + interval + "&limit=" + limit;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return new JSONArray(response.getBody());
    }

    public JSONObject getTicker24h(String symbol) {
        String pair = symbol.toUpperCase() + "USDT";
        String url = BASE_URL + "/ticker/24hr?symbol=" + pair;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return new JSONObject(response.getBody());
    }

    public JSONObject getPrice(String symbol) {
        String pair = symbol.toUpperCase() + "USDT";
        String url = BASE_URL + "/ticker/price?symbol=" + pair;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return new JSONObject(response.getBody());
    }
}
