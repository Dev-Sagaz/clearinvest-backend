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

        // ── 1. BRAPI — preço atual + múltiplos básicos (B3) ─────────────────
        String brapiJson = brapiClient.getQuote(ticker);
        JSONObject brapiRoot = new JSONObject(brapiJson);
        JSONObject quote = brapiRoot.getJSONArray("results").getJSONObject(0);

        double currentPrice    = quote.optDouble("regularMarketPrice", 0);
        double eps             = quote.optDouble("epsTrailingTwelveMonths", 0);
        double pbRatio         = quote.optDouble("priceToBook", 0);
        double dividendYield   = quote.optDouble("dividendYield", 0);
        double fiftyTwoWeekHigh = quote.optDouble("fiftyTwoWeekHigh", 0);
        double fiftyTwoWeekLow  = quote.optDouble("fiftyTwoWeekLow", 0);

        double peRatio   = eps > 0 ? round2(currentPrice / eps) : 0;
        double fairPrice = (fiftyTwoWeekHigh > 0 && fiftyTwoWeekLow > 0)
                ? round2((fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2.0) : 0;
        double upside    = fairPrice > 0
                ? round1(((fairPrice - currentPrice) / currentPrice) * 100) : 0;

        // ── 2. FMP — demonstrativos financeiros ──────────────────────────────
        // Ações B3 terminam em número (PETR4, VALE3…) — FMP não suporta ticker B3
        // diretamente no plano free, então os blocos abaixo vêm zerados por ora
        // e serão preenchidos quando integrar o ticker .SA ou ADR equivalente.
        String fmpTicker = ticker.toUpperCase();

        JSONObject income   = fetchFmpArray(fmpTicker, "income");
        JSONObject balance  = fetchFmpArray(fmpTicker, "balance");
        JSONObject cashflow = fetchFmpArray(fmpTicker, "cashflow");
        JSONObject metrics  = fetchFmpArray(fmpTicker, "metrics");

        // ── 3. Indicadores derivados do FMP ──────────────────────────────────
        double netMargin = round1(income.optDouble("netIncomeRatio", 0) * 100);
        double grossMargin = round1(income.optDouble("grossProfitRatio", 0) * 100);
        double roe  = round1(metrics.optDouble("roe", 0) * 100);
        double roic = round1(metrics.optDouble("roic", 0) * 100);

        // ── 4. Score e recomendação ───────────────────────────────────────────
        int score = calculateScore(peRatio, pbRatio, upside, netMargin, roe);
        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

        // ── 5. Montar resposta ────────────────────────────────────────────────
        StockAnalysis analysis = new StockAnalysis();

        // Identificação
        analysis.setTicker(ticker.toUpperCase());
        analysis.setCompanyName(quote.optString("longName", ticker));
        analysis.setMarket("B3");

        // Preço e valuation
        analysis.setCurrentPrice(currentPrice);
        analysis.setFairPrice(fairPrice);
        analysis.setUpsidePercent(upside);

        // Recomendação
        analysis.setScore(score);
        analysis.setRecommendation(recommendation);

        // Múltiplos (brapi)
        analysis.setPeRatio(peRatio);
        analysis.setPbRatio(round2(pbRatio));
        analysis.setDividendYield(dividendYield);

        // DRE (FMP)
        analysis.setRevenue(income.optDouble("revenue", 0));
        analysis.setGrossProfit(income.optDouble("grossProfit", 0));
        analysis.setOperatingIncome(income.optDouble("operatingIncome", 0));
        analysis.setNetIncome(income.optDouble("netIncome", 0));
        analysis.setEbitda(income.optDouble("ebitda", 0));
        analysis.setGrossMargin(grossMargin);
        analysis.setNetMargin(netMargin);

        // Balanço (FMP)
        analysis.setTotalAssets(balance.optDouble("totalAssets", 0));
        analysis.setTotalLiabilities(balance.optDouble("totalLiabilities", 0));
        analysis.setTotalEquity(balance.optDouble("totalStockholdersEquity", 0));
        analysis.setDebtToEquity(round2(metrics.optDouble("debtToEquity", 0)));
        analysis.setCurrentRatio(round2(metrics.optDouble("currentRatio", 0)));

        // DFC (FMP)
        analysis.setOperatingCashFlow(cashflow.optDouble("operatingCashFlow", 0));
        analysis.setFreeCashFlow(cashflow.optDouble("freeCashFlow", 0));
        analysis.setCapitalExpenditure(cashflow.optDouble("capitalExpenditure", 0));

        // Rentabilidade (FMP)
        analysis.setRoe(roe);
        analysis.setRoic(roic);
        analysis.setDebtToEbitda(round2(metrics.optDouble("netDebtToEBITDA", 0)));

        return analysis;
    }

    // ── Score de 0 a 100 ────────────────────────────────────────────────────
    private int calculateScore(double pe, double pb, double upside,
                               double netMargin, double roe) {
        int score = 50;

        // P/L
        if      (pe > 0 && pe < 8)   score += 15;
        else if (pe >= 8 && pe < 15)  score += 8;
        else if (pe >= 25)            score -= 10;

        // P/VP
        if      (pb > 0 && pb < 1)   score += 8;
        else if (pb >= 1 && pb < 2)   score += 4;
        else if (pb > 4)              score -= 5;

        // Upside vs preço justo
        if      (upside > 20) score += 15;
        else if (upside > 10) score += 8;
        else if (upside < 0)  score -= 10;

        // Margem líquida
        if      (netMargin > 20) score += 10;
        else if (netMargin > 10) score += 5;

        // ROE
        if      (roe > 15) score += 10;
        else if (roe > 10) score += 5;

        return Math.min(100, Math.max(0, score));
    }

    // ── Helper: busca o primeiro objeto de um endpoint FMP ──────────────────
    // Retorna JSONObject vazio (não null) se der erro — evita NullPointerException
    private JSONObject fetchFmpArray(String ticker, String type) {
        try {
            String json = switch (type) {
                case "income"   -> fmpClient.getIncomeStatement(ticker);
                case "balance"  -> fmpClient.getBalanceSheet(ticker);
                case "cashflow" -> fmpClient.getCashFlow(ticker);
                case "metrics"  -> fmpClient.getKeyMetrics(ticker);
                default -> "[]";
            };
            JSONArray arr = new JSONArray(json);
            return arr.length() > 0 ? arr.getJSONObject(0) : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }
}

