package com.clearinvest.backend.client;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class CoinGeckoClient {

    public JSONObject getCoinData(String coinId) {
        JSONObject mock = new JSONObject();
        mock.put("name", coinId);
        mock.put("symbol", coinId);
        mock.put("market_cap_rank", 1);
        JSONObject marketData = new JSONObject();
        JSONObject price = new JSONObject();
        price.put("usd", 100000.0);
        marketData.put("current_price", price);
        marketData.put("price_change_percentage_24h", 2.5);
        marketData.put("price_change_percentage_7d", 5.0);
        marketData.put("price_change_percentage_30d", 10.0);
        JSONObject marketCap = new JSONObject();
        marketCap.put("usd", 2000000000000.0);
        marketData.put("market_cap", marketCap);
        JSONObject volume = new JSONObject();
        volume.put("usd", 50000000000.0);
        marketData.put("total_volume", volume);
        JSONObject ath = new JSONObject();
        ath.put("usd", 109000.0);
        marketData.put("ath", ath);
        marketData.put("ath_change_percentage_usd", -8.0);
        marketData.put("circulating_supply", 19000000.0);
        marketData.put("max_supply", 21000000.0);
        mock.put("market_data", marketData);
        return mock;
    }

    public JSONArray getOhlcData(String coinId, int days) {
        return new JSONArray();
    }

    public String searchCoin(String query) {
        return "{}";
    }
}
