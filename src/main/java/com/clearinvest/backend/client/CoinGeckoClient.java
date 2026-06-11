package com.clearinvest.backend.client;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

// v3 - real CoinGecko client
@Component
public class CoinGeckoClient {

    private final RestTemplate restTemplate = new RestTemplate();
   private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    public JSONObject getCoinData(String coinId) {
        String url = BASE_URL + "/coins/" + coinId
                + "?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false";
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return new JSONObject(response.getBody());
    }

    public JSONArray getOhlcData(String coinId, int days) {
        String url = BASE_URL + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=" + days;
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return new JSONArray(response.getBody());
    }

    public String searchCoin(String query) {
        String url = BASE_URL + "/search?query=" + query;
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }
}
