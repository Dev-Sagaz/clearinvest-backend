package com.clearinvest.backend.service;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.client.FmpClient;
import com.clearinvest.backend.model.StockAnalysis;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private final BrapiClient brapiClient;
    private final FmpClient fmpClient;

    public AnalysisService(BrapiClient brapiClient, FmpClient fmpClient) {
        this.brapiClient = brapiClient;
        this.fmpClient = fmpClient;
    }

    public StockAnalysis analyze(String ticker) {

        // ── 1. BRAPI — leitura dos três níveis do JSON ───────────────────────
        String brapiJson = brapiClient.getQuote(ticker);
        JSONObject brapiRoot = new JSONObject(brapiJson);
        JSONObject quote = brapiRoot.getJSONArray("results").getJSONObject(0);

        // Sub-objetos retornados pela brapi com fundamental=true
        JSONObject keyStats = quote.optJSONObject("defaultKeyStatistics");
        JSONObject financial = quote.optJSONObject("financialData");
        if (keyStats == null)  keyStats  = new JSONObject();
        if (financial == null) financial = new JSONObject();

        // Nível raiz — quote
        double currentPrice     = quote.optDouble("regularMarketPrice", 0);
        double fiftyTwoWeekHigh = quote.optDouble("fiftyTwoWeekHigh", 0);
        double fiftyTwoWeekLow  = quote.optDouble("fiftyTwoWeekLow", 0);

        // defaultKeyStatistics
        double eps          = keyStats.optDouble("trailingEps", 0);
        double pbRatio      = keyStats.optDouble("priceToBook", 0);
        double dividendYield = keyStats.optDouble("dividendYield", 0);
        double bookValue    = keyStats.optDouble("bookValue", 0);

        // financialData
        double roe          = round1(financial.optDouble("returnOnEquity", 0) * 100);
        double netMargin    = round1(financial.optDouble("profitMargins", 0) * 100);
        double grossMargin  = round1(financial.optDouble("grossMargins", 0) * 100);
        double ebitda       = financial.optDouble("ebitda", 0);
        double revenue      = financial.optDouble("totalRevenue", 0);
        double grossProfit  = financial.optDouble("grossProfits", 0);
        double freeCashFlow = financial.optDouble("freeCashflow", 0);
        double opCashFlow   = financial.optDouble("operatingCashflow", 0);
        double debtToEquity = round2(financial.optDouble("debtToEquity", 0));
        double currentRatio = round2(financial.optDouble("currentRatio", 0));

        // ── 2. Cálculos derivados ────────────────────────────────────────────
        double peRatio   = eps > 0 ? round2(currentPrice / eps) : 0;
        double fairPrice = (fiftyTwoWeekHigh > 0 && fiftyTwoWeekLow > 0)
                ? round2((fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2.0) : 0;
        double upside    = fairPrice > 0
                ? round1(((fairPrice - currentPrice) / currentPrice) * 100) : 0;

        // ── 3. Score e recomendação ──────────────────────────────────────────
        int score = calculateScore(peRatio, pbRatio, upside, netMargin, roe);
        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

        // ── 4. Montar resposta ───────────────────────────────────────────────
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
        analysis.setPbRatio(round2(pbRatio));
        analysis.setDividendYield(round2(dividendYield * 100)); // ex: 0.07 → 7.0%

        analysis.setRevenue(revenue);
        analysis.setGrossProfit(grossProfit);
        analysis.setNetIncome(0); // não retornado diretamente pela brapi
        analysis.setEbitda(ebitda);
        analysis.setGrossMargin(grossMargin);
        analysis.setNetMargin(netMargin);
        analysis.setOperatingIncome(0);

        analysis.setTotalAssets(0);
        analysis.setTotalLiabilities(0);
        analysis.setTotalEquity(0);
        analysis.setDebtToEquity(debtToEquity);
        analysis.setCurrentRatio(currentRatio);

        analysis.setOperatingCashFlow(opCashFlow);
        analysis.setFreeCashFlow(freeCashFlow);
        analysis.setCapitalExpenditure(0);

        analysis.setRoe(roe);
        analysis.setRoic(0);
        analysis.setDebtToEbitda(0);

        return analysis;
    }

    // ── Score de 0 a 100 ────────────────────────────────────────────────────
    private int calculateScore(double pe, double pb, double upside,
                               double netMargin, double roe) {
        int score = 50;

        if      (pe > 0 && pe < 8)  score += 15;
        else if (pe >= 8 && pe < 15) score += 8;
        else if (pe >= 25)           score -= 10;

        if      (pb > 0 && pb < 1)  score += 8;
        else if (pb >= 1 && pb < 2)  score += 4;
        else if (pb > 4)             score -= 5;

        if      (upside > 20) score += 15;
        else if (upside > 10) score += 8;
        else if (upside < 0)  score -= 10;

        if      (netMargin > 20) score += 10;
        else if (netMargin > 10) score += 5;

        if      (roe > 15) score += 10;
        else if (roe > 10) score += 5;

        return Math.min(100, Math.max(0, score));
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }
}

