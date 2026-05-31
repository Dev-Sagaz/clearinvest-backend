
package com.clearinvest.backend.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BrapiClient {

    @Value("${brapi.token}")
    private String token;

    @Value("${brapi.url}")
    private String brapiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getQuote(String ticker) {
        String url = brapiUrl
                + "/quote/" + ticker.toUpperCase()
                + "?token=" + token
                + "&fundamental=true"
                + "&modules=summaryProfile,defaultKeyStatistics,financialData";
        return restTemplate.getForObject(url, String.class);
    }
}