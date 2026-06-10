package com.clearinvest.backend.service;

import com.clearinvest.backend.client.CoinGeckoClient;
import com.clearinvest.backend.model.CryptoAnalysis;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    private final CoinGeckoClient coinGeckoClient;

    public CryptoService(CoinGeckoClient coinGeckoClient) {
        this.coinGeckoClient = coinGeckoClient;
    }

    public CryptoAnalysis analyze(String symbol) {
        try {
            String coinId = resolveCoinId(symbol);
            JSONObject data = coinGeckoClient.getCoinData(coinId);
            JSONObject marketData = data.optJSONObject("market_data");

            CryptoAnalysis analysis = new CryptoAnalysis();
            analysis.setId(coinId);
            analysis.setSymbol(symbol.toUpperCase());
            analysis.setName(data.optString("name", symbol));
            analysis.setCurrentPrice(getUsd(marketData, "current_price"));
            analysis.setPriceChangePercent24h(marketData != null ? marketData.optDouble("price_change_percentage_24h", 0) : 0);
            analysis.setPriceChangePercent7d(marketData != null ? marketData.optDouble("price_change_percentage_7d", 0) : 0);
            analysis.setPriceChangePercent30d(marketData != null ? marketData.optDouble("price_change_percentage_30d", 0) : 0);
            analysis.setMarketCap(getUsd(marketData, "market_cap"));
            analysis.setMarketCapRank(data.optLong("market_cap_rank", 0));
            analysis.setVolume24h(getUsd(marketData, "total_volume"));
            analysis.setAth(getUsd(marketData, "ath"));
            analysis.setAthChangePercent(marketData != null ? marketData.optDouble("ath_change_percentage_usd", 0) : 0);
            analysis.setCirculatingSupply(marketData != null ? marketData.optDouble("circulating_supply", 0) : 0);
            analysis.setMaxSupply(marketData != null ? marketData.optDouble("max_supply", 0) : 0);
            analysis.setRecommendation("Neutro");
            analysis.setScore(50);
            analysis.setSignal("Neutro");
            analysis.setTrend("Lateral");
            return analysis;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar cripto: " + e.getMessage());
        }
    }

    private String resolveCoinId(String symbol) {
        return switch (symbol.toLowerCase()) {
            case "btc", "bitcoin"    -> "bitcoin";
            case "eth", "ethereum"   -> "ethereum";
            case "sol", "solana"     -> "solana";
            case "bnb"               -> "binancecoin";
            case "xrp", "ripple"     -> "ripple";
            case "ada", "cardano"    -> "cardano";
            case "doge", "dogecoin"  -> "dogecoin";
            case "dot", "polkadot"   -> "polkadot";
            case "avax", "avalanche" -> "avalanche-2";
            case "link", "chainlink" -> "chainlink";
            case "ltc", "litecoin"   -> "litecoin";
            case "matic", "polygon"  -> "matic-network";
            case "near"              -> "near";
            case "sui"               -> "sui";
            case "pepe"              -> "pepe";
            default                  -> symbol.toLowerCase();
        };
    }

    private double getUsd(JSONObject obj, String key) {
        if (obj == null) return 0;
        JSONObject inner = obj.optJSONObject(key);
        if (inner != null) return inner.optDouble("usd", 0);
        return obj.optDouble(key, 0);
    }
}
