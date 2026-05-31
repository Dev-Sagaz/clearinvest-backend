package com.clearinvest.backend.service;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.client.FmpClient;
import com.clearinvest.backend.client.FundamentusClient;
import com.clearinvest.backend.model.StockAnalysis;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AnalysisService {

    private final BrapiClient brapiClient;
    private final FmpClient fmpClient;
    private final FundamentusClient fundamentusClient;

    public AnalysisService(BrapiClient brapiClient, FmpClient fmpClient, FundamentusClient fundamentusClient) {
        this.brapiClient = brapiClient;
        this.fmpClient = fmpClient;
        this.fundamentusClient = fundamentusClient;
    }

    public StockAnalysis analyze(String ticker) {

        // ── 1. BRAPI — preço atual ───────────────────────────────────────────
        String brapiJson = brapiClient.getQuote(ticker);
        JSONObject brapiRoot = new JSONObject(brapiJson);
        JSONArray results = brapiRoot.optJSONArray("results");
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("Ticker não encontrado: " + ticker);
        }
        JSONObject quote = results.getJSONObject(0);

        double currentPrice     = quote.optDouble("regularMarketPrice", 0);
        double fiftyTwoWeekHigh = quote.optDouble("fiftyTwoWeekHigh", 0);
        double fiftyTwoWeekLow  = quote.optDouble("fiftyTwoWeekLow", 0);

        // ── 2. FUNDAMENTUS — indicadores fundamentalistas ────────────────────
        Map<String, Double> fund = fundamentusClient.getIndicators(ticker);

        double peRatio      = fund.getOrDefault("P/L", quote.optDouble("priceEarnings", 0));
        double pbRatio      = fund.getOrDefault("P/VP", 0.0);
        double dividendYield = fund.getOrDefault("Div. Yield", 0.0);
        double roe          = fund.getOrDefault("ROE", 0.0);
        double roic         = fund.getOrDefault("ROIC", 0.0);
        double netMargin    = fund.getOrDefault("Marg. Líquida", 0.0);
        double grossMargin  = fund.getOrDefault("Marg. Bruta", 0.0);
        double debtToEbitda = fund.getOrDefault("Dív. Líquida/EBITDA", 0.0);
        double currentRatio = fund.getOrDefault("Liquidez Corr", 0.0);
        double debtToEquity = fund.getOrDefault("Dív Líq / Patrim", 0.0);
        double revenue      = fund.getOrDefault("Receita Líquida", 0.0);
        double ebitda       = fund.getOrDefault("EBIT", 0.0);
        double netIncome    = fund.getOrDefault("Lucro Líquido", 0.0);
        double totalAssets  = fund.getOrDefault("Ativo", 0.0);
        double totalEquity  = fund.getOrDefault("Patrim. Liq", 0.0);

        // ── 3. Cálculos derivados ────────────────────────────────────────────
        double eps       = peRatio > 0 ? round2(currentPrice / peRatio) : 0;
        double fairPrice = (fiftyTwoWeekHigh > 0 && fiftyTwoWeekLow > 0)
                ? round2((fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2.0) : 0;
        double upside    = fairPrice > 0
                ? round1(((fairPrice - currentPrice) / currentPrice) * 100) : 0;

        // ── 4. Score e recomendação ──────────────────────────────────────────
        int score = calculateScore(peRatio, pbRatio, upside, netMargin, roe, dividendYield);
        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

        // ── 5. Montar resposta ───────────────────────────────────────────────
        StockAnalysis analysis = new StockAnalysis();

        analysis.setTicker(ticker.toUpperCase());
        analysis.setCompanyName(quote.optString("longName", ticker));
        analysis.setMarket("B3");

        analysis.setCurrentPrice(currentPrice);
        analysis.setFairPrice(fairPrice);
        analysis.setUpsidePercent(upside);

        analysis.setScore(score);
        analysis.setRecommendation(recommendation);

        analysis.setPeRatio(round2(peRatio));
        analysis.setPbRatio(round2(pbRatio));
        analysis.setDividendYield(round2(dividendYield));

        analysis.setRevenue(revenue);
        analysis.setGrossProfit(0);
        analysis.setNetIncome(netIncome);
        analysis.setEbitda(ebitda);
        analysis.setGrossMargin(round2(grossMargin));
        analysis.setNetMargin(round2(netMargin));
        analysis.setOperatingIncome(0);

        analysis.setTotalAssets(totalAssets);
        analysis.setTotalLiabilities(0);
        analysis.setTotalEquity(totalEquity);
        analysis.setDebtToEquity(round2(debtToEquity));
        analysis.setCurrentRatio(round2(currentRatio));

        analysis.setOperatingCashFlow(0);
        analysis.setFreeCashFlow(0);
        analysis.setCapitalExpenditure(0);

        analysis.setRoe(round2(roe));
        analysis.setRoic(round2(roic));
        analysis.setDebtToEbitda(round2(debtToEbitda));

        return analysis;
    }

    // ── Score de 0 a 100 ────────────────────────────────────────────────────
    private int calculateScore(double pe, double pb, double upside,
                               double netMargin, double roe, double dy) {
        int score = 50;

        // P/L
        if      (pe > 0 && pe < 8)   score += 15;
        else if (pe >= 8 && pe < 15)  score += 8;
        else if (pe >= 25)            score -= 10;

        // P/VP
        if      (pb > 0 && pb < 1)   score += 8;
        else if (pb >= 1 && pb < 2)   score += 4;
        else if (pb > 4)              score -= 5;

        // Upside
        if      (upside > 20) score += 15;
        else if (upside > 10) score += 8;
        else if (upside < 0)  score -= 10;

        // Margem líquida
        if      (netMargin > 20) score += 10;
        else if (netMargin > 10) score += 5;

        // ROE
        if      (roe > 15) score += 10;
        else if (roe > 10) score += 5;

        // Dividend Yield — bônus para pagadoras de dividendos
        if      (dy > 8)  score += 8;
        else if (dy > 5)  score += 4;

        return Math.min(100, Math.max(0, score));
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }
}

