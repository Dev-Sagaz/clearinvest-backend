package com.clearinvest.backend.service;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.model.StockAnalysis;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private final BrapiClient brapiClient;

    public AnalysisService(BrapiClient brapiClient) {
        this.brapiClient = brapiClient;
    }

    public StockAnalysis analyze(String ticker) {
        String brapiJson = brapiClient.getQuote(ticker);
        JSONObject brapiRoot = new JSONObject(brapiJson);
        JSONObject quote = brapiRoot.getJSONArray("results").getJSONObject(0);

        double currentPrice = quote.optDouble("regularMarketPrice", 0);
        double eps = quote.optDouble("earningsPerShare", 0);
        double peRatio = (eps > 0) ? Math.round((currentPrice / eps) * 100.0) / 100.0 : 0;
        double fiftyTwoWeekHigh = quote.optDouble("fiftyTwoWeekHigh", 0);
        double fiftyTwoWeekLow = quote.optDouble("fiftyTwoWeekLow", 0);
        double marketCap = quote.optDouble("marketCap", 0);
        double changePercent = quote.optDouble("regularMarketChangePercent", 0);

        // Preço justo baseado na média 52 semanas
        double fairPrice = (fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2;
        fairPrice = Math.round(fairPrice * 100.0) / 100.0;
        double upside = fairPrice > 0 ? Math.round(((fairPrice - currentPrice) / currentPrice) * 1000.0) / 10.0 : 0;

        // Score melhorado
        int score = calculateScore(peRatio, upside, changePercent);
        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

        StockAnalysis analysis = new StockAnalysis();
        analysis.setTicker(ticker.toUpperCase());
        analysis.setCompanyName(quote.optString("longName", ticker));
        analysis.setMarket("B3");
        analysis.setCurrentPrice(currentPrice);
        analysis.setFairPrice(fairPrice);
        analysis.setUpsidePercent(upside);
        analysis.setScore(score);
        analysis.setRecommendation(recommendation);
        analysis.setPeRatio(peRatio);
        analysis.setDividendYield(quote.optDouble("dividendYield", 0));

        return analysis;
    }

    private int calculateScore(double pe, double upside, double changePercent) {
        int score = 50;

        // P/L
        if (pe > 0 && pe < 8) score += 20;
        else if (pe >= 8 && pe < 15) score += 12;
        else if (pe >= 15 && pe < 25) score += 5;
        else if (pe >= 25) score -= 10;

        // Upside vs preço justo
        if (upside > 20) score += 20;
        else if (upside > 10) score += 10;
        else if (upside > 0) score += 5;
        else score -= 10;

        // Momentum
        if (changePercent > 2) score += 5;
        else if (changePercent < -2) score -= 5;

        return Math.min(100, Math.max(0, score));
    }
}
