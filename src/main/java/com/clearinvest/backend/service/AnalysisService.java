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

    public StockAnalysis analyze(String ticker, String mode) {

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

        // ── 2. FUNDAMENTUS — todos os indicadores ────────────────────────────
        Map<String, Double> fund = fundamentusClient.getIndicators(ticker);

        double peRatio       = fund.getOrDefault("P/L",               quote.optDouble("priceEarnings", 0));
        double pbRatio       = fund.getOrDefault("P/VP",              0.0);
        double dividendYield = fund.getOrDefault("Div. Yield",        0.0);
        double roe           = fund.getOrDefault("ROE",               0.0);
        double roic          = fund.getOrDefault("ROIC",              0.0);
        double netMargin     = fund.getOrDefault("Marg. Líquida",     0.0);
        double grossMargin   = fund.getOrDefault("Marg. Bruta",       0.0);
        double ebitMargin    = fund.getOrDefault("Marg. EBIT",        0.0);
        double currentRatio  = fund.getOrDefault("Liquidez Corr",     0.0);
        double debtToEquity  = fund.getOrDefault("Dív Líq / Patrim",  0.0);
        double debtToEbitda  = fund.getOrDefault("EV / EBITDA",       0.0);
        double revenueGrowth = fund.getOrDefault("Cres. Rec (5a)",    0.0);
        double revenue       = fund.getOrDefault("Receita Líquida",   0.0);
        double ebitda        = fund.getOrDefault("EBIT",              0.0);
        double netIncome     = fund.getOrDefault("Lucro Líquido",     0.0);
        double totalAssets   = fund.getOrDefault("Ativo",             0.0);
        double totalEquity   = fund.getOrDefault("Patrim. Liq",       0.0);
        double freeCashFlow  = fund.getOrDefault("Div. Líquida",      0.0);
        double psr           = fund.getOrDefault("PSR",               0.0);
        double ebitOnAssets  = fund.getOrDefault("EBIT / Ativo",      0.0);

        // ── 3. Preço justo (média 52 semanas) ────────────────────────────────
        double fairPrice = (fiftyTwoWeekHigh > 0 && fiftyTwoWeekLow > 0)
                ? round2((fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2.0) : 0;
        double upside = fairPrice > 0
                ? round1(((fairPrice - currentPrice) / currentPrice) * 100) : 0;

        // ── 4. Score conforme o modo ─────────────────────────────────────────
        int score;
        if ("barsi".equalsIgnoreCase(mode)) {
            score = calculateBarsiScore(peRatio, pbRatio, dividendYield, roe, currentRatio, debtToEquity, netMargin, upside);
        } else if ("buffett".equalsIgnoreCase(mode)) {
            score = calculateBuffettScore(roe, roic, netMargin, debtToEquity, peRatio, revenueGrowth, currentRatio, upside, ebitOnAssets);
        } else {
            score = calculateDefaultScore(peRatio, pbRatio, upside, netMargin, roe, dividendYield);
        }

        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

        // ── 5. Montar resposta ───────────────────────────────────────────────
        StockAnalysis analysis = new StockAnalysis();

        analysis.setTicker(ticker.toUpperCase());
        analysis.setCompanyName(quote.optString("longName", ticker));
        analysis.setMarket("B3");
        analysis.setMode(mode != null ? mode : "default");

        analysis.setCurrentPrice(currentPrice);
        analysis.setFairPrice(fairPrice);
        analysis.setUpsidePercent(upside);

        analysis.setScore(score);
        analysis.setRecommendation(recommendation);

        analysis.setPeRatio(round2(peRatio));
        analysis.setPbRatio(round2(pbRatio));
        analysis.setDividendYield(round2(dividendYield));
        analysis.setPsr(round2(psr));

        analysis.setRevenue(revenue);
        analysis.setGrossProfit(0);
        analysis.setNetIncome(netIncome);
        analysis.setEbitda(ebitda);
        analysis.setGrossMargin(round2(grossMargin));
        analysis.setNetMargin(round2(netMargin));
        analysis.setEbitMargin(round2(ebitMargin));
        analysis.setOperatingIncome(0);
        analysis.setRevenueGrowth5y(round2(revenueGrowth));

        analysis.setTotalAssets(totalAssets);
        analysis.setTotalLiabilities(0);
        analysis.setTotalEquity(totalEquity);
        analysis.setDebtToEquity(round2(debtToEquity));
        analysis.setCurrentRatio(round2(currentRatio));

        analysis.setOperatingCashFlow(0);
        analysis.setFreeCashFlow(freeCashFlow);
        analysis.setCapitalExpenditure(0);

        analysis.setRoe(round2(roe));
        analysis.setRoic(round2(roic));
        analysis.setDebtToEbitda(round2(debtToEbitda));

        return analysis;
    }

    // ── Modo Barsi — foco em dividendos ─────────────────────────────────────
    private int calculateBarsiScore(double pe, double pb, double dy, double roe,
                                    double currentRatio, double debtToEquity,
                                    double netMargin, double upside) {
        int score = 40;

        // Dividend Yield — peso máximo no método Barsi
        if      (dy >= 8)  score += 25;
        else if (dy >= 6)  score += 18;
        else if (dy >= 4)  score += 8;
        else if (dy < 2)   score -= 15;

        // P/VP — não pagar caro pelo patrimônio
        if      (pb > 0 && pb < 1)  score += 12;
        else if (pb >= 1 && pb < 2)  score += 7;
        else if (pb >= 2 && pb < 3)  score += 2;
        else if (pb >= 3)            score -= 8;

        // P/L — preço razoável
        if      (pe > 0 && pe < 8)   score += 10;
        else if (pe >= 8 && pe < 15)  score += 5;
        else if (pe >= 15 && pe < 25) score += 1;
        else if (pe >= 25)            score -= 8;

        // ROE — empresa rentável
        if      (roe >= 15) score += 8;
        else if (roe >= 10) score += 4;
        else if (roe < 5)   score -= 5;

        // Liquidez — empresa saudável
        if      (currentRatio >= 2)  score += 5;
        else if (currentRatio >= 1)  score += 2;
        else if (currentRatio < 0.8) score -= 5;

        // Dívida controlada
        if      (debtToEquity < 0.3) score += 5;
        else if (debtToEquity > 1.0) score -= 8;

        // Margem líquida mínima
        if      (netMargin >= 20) score += 5;
        else if (netMargin < 5)   score -= 5;

        return Math.min(100, Math.max(0, score));
    }

    // ── Modo Buffett — foco em qualidade ────────────────────────────────────
    private int calculateBuffettScore(double roe, double roic, double netMargin,
                                      double debtToEquity, double pe,
                                      double revenueGrowth, double currentRatio,
                                      double upside, double ebitOnAssets) {
        int score = 40;

        // ROE — rentabilidade consistente (peso máximo no método Buffett)
        if      (roe >= 20) score += 20;
        else if (roe >= 15) score += 14;
        else if (roe >= 10) score += 7;
        else if (roe < 8)   score -= 10;

        // ROIC — alocação eficiente de capital
        if      (roic >= 15) score += 15;
        else if (roic >= 10) score += 8;
        else if (roic < 5)   score -= 8;

        // Margem líquida — negócio eficiente
        if      (netMargin >= 20) score += 12;
        else if (netMargin >= 10) score += 6;
        else if (netMargin < 5)   score -= 8;

        // Dívida baixa — empresa conservadora
        if      (debtToEquity < 0.3) score += 10;
        else if (debtToEquity < 0.5) score += 5;
        else if (debtToEquity > 1.0) score -= 10;

        // P/L — não pagar caro
        if      (pe > 0 && pe < 15) score += 8;
        else if (pe >= 15 && pe < 25) score += 3;
        else if (pe >= 25)            score -= 5;

        // Crescimento de receita (5 anos)
        if      (revenueGrowth >= 10) score += 8;
        else if (revenueGrowth >= 5)  score += 4;
        else if (revenueGrowth < 0)   score -= 8;

        // EBIT/Ativo — eficiência operacional
        if      (ebitOnAssets >= 15) score += 5;
        else if (ebitOnAssets >= 10) score += 2;

        return Math.min(100, Math.max(0, score));
    }

    // ── Modo padrão ──────────────────────────────────────────────────────────
    private int calculateDefaultScore(double pe, double pb, double upside,
                                      double netMargin, double roe, double dy) {
        int score = 50;
        if      (pe > 0 && pe < 8)   score += 15;
        else if (pe >= 8 && pe < 15)  score += 8;
        else if (pe >= 25)            score -= 10;
        if      (pb > 0 && pb < 1)   score += 8;
        else if (pb >= 1 && pb < 2)   score += 4;
        else if (pb > 4)              score -= 5;
        if      (upside > 20) score += 15;
        else if (upside > 10) score += 8;
        else if (upside < 0)  score -= 10;
        if      (netMargin > 20) score += 10;
        else if (netMargin > 10) score += 5;
        if      (roe > 15) score += 10;
        else if (roe > 10) score += 5;
        if      (dy > 8)  score += 8;
        else if (dy > 5)  score += 4;
        return Math.min(100, Math.max(0, score));
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round1(double v) { return Math.round(v * 10.0)  / 10.0;  }
}

