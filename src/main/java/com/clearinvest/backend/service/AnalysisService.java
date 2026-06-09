package com.clearinvest.backend.service;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.client.FmpClient;
import com.clearinvest.backend.client.FundamentusClient;
import com.clearinvest.backend.model.StockAnalysis;
import com.clearinvest.backend.model.ValuationResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        double totalEquity = fund.getOrDefault("Patrim. Líq", 0.0);
        double currentAssets = fund.getOrDefault("Ativo Circulante",  0.0);
        double cash          = fund.getOrDefault("Disponibilidades",   0.0);
        double grossDebt     = fund.getOrDefault("Dív. Bruta",         0.0);
        double netDebt       = fund.getOrDefault("Dív. Líquida",       0.0);
        double psr           = fund.getOrDefault("PSR",               0.0);
        double ebitOnAssets  = fund.getOrDefault("EBIT / Ativo",      0.0);
        double bookValue     = fund.getOrDefault("VPA",               0.0);
        double eps           = fund.getOrDefault("LPA",               0.0);

        double avg52w = (fiftyTwoWeekHigh > 0 && fiftyTwoWeekLow > 0)
                ? round2((fiftyTwoWeekHigh + fiftyTwoWeekLow) / 2.0) : 0;

        double grahamPrice = (eps > 0 && bookValue > 0)
                ? round2(Math.sqrt(22.5 * eps * bookValue)) : 0;

        double fairPrice = switch (mode.toLowerCase()) {
            case "graham"      -> grahamPrice > 0 ? grahamPrice : avg52w;
            case "buffett"     -> eps > 0 ? round2(eps * (peRatio < 10 ? 12 : 15)) : avg52w;
            case "lynch"       -> (eps > 0 && revenueGrowth > 0) ? round2(eps * revenueGrowth) : avg52w;
            case "clearinvest" -> grahamPrice > 0 && avg52w > 0 ? round2((grahamPrice + avg52w) / 2.0) : avg52w;
            default            -> avg52w;
        };

        double upside = fairPrice > 0
                ? round1(((fairPrice - currentPrice) / currentPrice) * 100) : 0;

        int score = switch (mode.toLowerCase()) {
            case "barsi"       -> calculateBarsiScore(peRatio, pbRatio, dividendYield, roe, currentRatio, debtToEquity, netMargin, upside);
            case "buffett"     -> calculateBuffettScore(roe, roic, netMargin, debtToEquity, peRatio, revenueGrowth, currentRatio, upside, ebitOnAssets);
            case "lynch"       -> calculateLynchScore(peRatio, revenueGrowth, netMargin, currentRatio, debtToEquity, roic, upside);
            case "graham"      -> calculateGrahamScore(peRatio, pbRatio, upside, currentRatio, debtToEquity, dividendYield, netMargin);
            case "clearinvest" -> calculateClearInvestScore(peRatio, pbRatio, dividendYield, roe, roic, netMargin, debtToEquity, debtToEbitda, currentRatio, revenueGrowth, upside, grossMargin);
            default            -> calculateDefaultScore(peRatio, pbRatio, upside, netMargin, roe, dividendYield);
        };

        String recommendation = score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar";

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
        analysis.setCurrentAssets(currentAssets);
        analysis.setCash(cash);
        analysis.setGrossDebt(grossDebt);
        analysis.setNetDebt(netDebt);
        analysis.setDebtToEquity(round2(debtToEquity));
        analysis.setCurrentRatio(round2(currentRatio));
        analysis.setOperatingCashFlow(0);
        analysis.setFreeCashFlow(0);
        analysis.setCapitalExpenditure(0);
        analysis.setRoe(round2(roe));
        analysis.setRoic(round2(roic));
        analysis.setDebtToEbitda(round2(debtToEbitda));
        analysis.setEvEbit(round2(fund.getOrDefault("EV / EBIT", 0.0)));
        analysis.setPEbit(round2(fund.getOrDefault("P/EBIT", 0.0)));
        analysis.setPAtivos(round2(fund.getOrDefault("P/Ativos", 0.0)));
        analysis.setGiroAtivos(round2(fund.getOrDefault("Giro Ativos", 0.0)));
        analysis.setPCapGiro(round2(fund.getOrDefault("P/Cap. Giro", 0.0)));
        analysis.setEbitAtivo(round2(fund.getOrDefault("EBIT / Ativo", 0.0)));
        analysis.setMin52w(fiftyTwoWeekLow);
        analysis.setMax52w(fiftyTwoWeekHigh);
        analysis.setVolumeMedio(quote.optDouble("regularMarketVolume", 0));
        analysis.setValorMercado(fund.getOrDefault("Valor de mercado", 0.0));
        analysis.setValorFirma(fund.getOrDefault("Valor da firma", 0.0));

        analysis.setValuationRobusto(calcularValuationRobusto(
                currentPrice, eps, bookValue, dividendYield,
                peRatio, revenueGrowth, ebitda, analysis.getValorFirma(), netIncome
        ));

        return analysis;
    }

    private ValuationResult calcularValuationRobusto(
            double currentPrice, double eps, double bookValue,
            double dividendYield, double peRatio, double revenueGrowth,
            double ebitda, double valorFirma, double netIncome) {

        ValuationResult result = new ValuationResult();
        List<ValuationResult.ValuationMethod> methods = new ArrayList<>();

        // 1. Graham
        ValuationResult.ValuationMethod graham = new ValuationResult.ValuationMethod();
        graham.setNome("Graham");
        graham.setDescricao("√(22,5 × LPA × VPA) — margem de segurança patrimonial");
        if (eps > 0 && bookValue > 0) {
            double pj = round2(Math.sqrt(22.5 * eps * bookValue));
            graham.setPrecoJusto(pj);
            graham.setDisponivel(true);
            graham.setStatus(currentPrice < pj ? "Subvalorizada"
                    : currentPrice > pj * 1.05 ? "Sobrevalorizada" : "Neutro");
        } else {
            graham.setPrecoJusto(0);
            graham.setDisponivel(false);
            graham.setStatus("Sem dados");
        }
        methods.add(graham);

        // 2. Bazin
        ValuationResult.ValuationMethod bazin = new ValuationResult.ValuationMethod();
        bazin.setNome("Bazin");
        bazin.setDescricao("Dividendo anual / 6% — preço justo pelo yield mínimo");
        if (dividendYield > 0 && currentPrice > 0) {
            double dividendoAnual = currentPrice * (dividendYield / 100.0);
            double pj = round2(dividendoAnual / 0.06);
            bazin.setPrecoJusto(pj);
            bazin.setDisponivel(true);
            bazin.setStatus(currentPrice < pj ? "Subvalorizada"
                    : currentPrice > pj * 1.05 ? "Sobrevalorizada" : "Neutro");
        } else {
            bazin.setPrecoJusto(0);
            bazin.setDisponivel(false);
            bazin.setStatus("Sem dados");
        }
        methods.add(bazin);

        // 3. P/L Justo
        ValuationResult.ValuationMethod plJusto = new ValuationResult.ValuationMethod();
        plJusto.setNome("P/L Justo");
        plJusto.setDescricao("LPA × 15 — múltiplo histórico justo do mercado");
        if (eps > 0) {
            double pj = round2(eps * 15);
            plJusto.setPrecoJusto(pj);
            plJusto.setDisponivel(true);
            plJusto.setStatus(currentPrice < pj ? "Subvalorizada"
                    : currentPrice > pj * 1.05 ? "Sobrevalorizada" : "Neutro");
        } else {
            plJusto.setPrecoJusto(0);
            plJusto.setDisponivel(false);
            plJusto.setStatus("Sem dados");
        }
        methods.add(plJusto);

        // 4. FCD Simplificado
        ValuationResult.ValuationMethod fcd = new ValuationResult.ValuationMethod();
        fcd.setNome("FCD Simplificado");
        fcd.setDescricao("LPA × (1+g) / (WACC - g) — valor presente dos lucros futuros");
        double g = revenueGrowth > 0 ? Math.min(revenueGrowth / 100.0, 0.15) : 0.05;
        double wacc = 0.13;
        if (eps > 0 && wacc > g) {
            double pj = round2((eps * (1 + g)) / (wacc - g));
            fcd.setPrecoJusto(pj);
            fcd.setDisponivel(true);
            fcd.setStatus(currentPrice < pj ? "Subvalorizada"
                    : currentPrice > pj * 1.05 ? "Sobrevalorizada" : "Neutro");
        } else {
            fcd.setPrecoJusto(0);
            fcd.setDisponivel(false);
            fcd.setStatus("Sem dados");
        }
        methods.add(fcd);

        // 5. EV/EBIT
        ValuationResult.ValuationMethod evEbitMethod = new ValuationResult.ValuationMethod();
        evEbitMethod.setNome("EV/EBIT");
        evEbitMethod.setDescricao("EBIT × 10 / base acionária — valor pelo lucro operacional");
        if (ebitda > 0 && valorFirma > 0 && currentPrice > 0) {
            double acoesImplicitas = valorFirma / currentPrice;
            double pj = acoesImplicitas > 0 ? round2((ebitda * 10.0) / acoesImplicitas) : 0;
            evEbitMethod.setPrecoJusto(pj);
            evEbitMethod.setDisponivel(pj > 0);
            evEbitMethod.setStatus(pj > 0
                    ? (currentPrice < pj ? "Subvalorizada"
                    : currentPrice > pj * 1.05 ? "Sobrevalorizada" : "Neutro")
                    : "Sem dados");
        } else {
            evEbitMethod.setPrecoJusto(0);
            evEbitMethod.setDisponivel(false);
            evEbitMethod.setStatus("Sem dados");
        }
        methods.add(evEbitMethod);

        // Consolidação
        List<Double> precos = methods.stream()
                .filter(ValuationResult.ValuationMethod::isDisponivel)
                .filter(m -> m.getPrecoJusto() > 0)
                .map(ValuationResult.ValuationMethod::getPrecoJusto)
                .toList();

        long favoraveis = methods.stream()
                .filter(m -> "Subvalorizada".equals(m.getStatus()))
                .count();

        double min  = precos.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max  = precos.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double media = precos.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        String consenso = favoraveis >= 4 ? "COMPRAR"
                : favoraveis == 3 ? "NEUTRO POSITIVO"
                : favoraveis == 2 ? "NEUTRO"
                : "EVITAR";

        result.setMethods(methods);
        result.setPrecoAtual(currentPrice);
        result.setRangeMin(round2(min));
        result.setRangeMax(round2(max));
        result.setMediaPonderada(round2(media));
        result.setMetodosFavoraveis((int) favoraveis);
        result.setConsenso(consenso);

        return result;
    }

    private int calculateBarsiScore(double pe, double pb, double dy, double roe,
                                    double currentRatio, double debtToEquity,
                                    double netMargin, double upside) {
        int score = 40;
        if      (dy >= 8)  score += 25;
        else if (dy >= 6)  score += 18;
        else if (dy >= 4)  score += 8;
        else if (dy < 2)   score -= 15;
        if      (pb > 0 && pb < 1)    score += 12;
        else if (pb >= 1 && pb < 2)   score += 7;
        else if (pb >= 2 && pb < 3)   score += 2;
        else if (pb >= 3)             score -= 8;
        if      (pe > 0 && pe < 8)    score += 10;
        else if (pe >= 8 && pe < 15)  score += 5;
        else if (pe >= 25)            score -= 8;
        if      (roe >= 15) score += 8;
        else if (roe >= 10) score += 4;
        else if (roe < 5)   score -= 5;
        if      (currentRatio >= 2)   score += 5;
        else if (currentRatio >= 1)   score += 2;
        else if (currentRatio < 0.8)  score -= 5;
        if      (debtToEquity < 0.3)  score += 5;
        else if (debtToEquity > 1.0)  score -= 8;
        if      (netMargin >= 20) score += 5;
        else if (netMargin < 5)   score -= 5;
        return Math.min(100, Math.max(0, score));
    }

    private int calculateBuffettScore(double roe, double roic, double netMargin,
                                      double debtToEquity, double pe,
                                      double revenueGrowth, double currentRatio,
                                      double upside, double ebitOnAssets) {
        int score = 40;
        if      (roe >= 20) score += 20;
        else if (roe >= 15) score += 14;
        else if (roe >= 10) score += 7;
        else if (roe < 8)   score -= 10;
        if      (roic >= 15) score += 15;
        else if (roic >= 10) score += 8;
        else if (roic < 5)   score -= 8;
        if      (netMargin >= 20) score += 12;
        else if (netMargin >= 10) score += 6;
        else if (netMargin < 5)   score -= 8;
        if      (debtToEquity < 0.3) score += 10;
        else if (debtToEquity < 0.5) score += 5;
        else if (debtToEquity > 1.0) score -= 10;
        if      (pe > 0 && pe < 15)   score += 8;
        else if (pe >= 15 && pe < 25)  score += 3;
        else if (pe >= 25)             score -= 5;
        if      (revenueGrowth >= 10) score += 8;
        else if (revenueGrowth >= 5)  score += 4;
        else if (revenueGrowth < 0)   score -= 8;
        if      (ebitOnAssets >= 15) score += 5;
        else if (ebitOnAssets >= 10) score += 2;
        return Math.min(100, Math.max(0, score));
    }

    private int calculateLynchScore(double pe, double revenueGrowth, double netMargin,
                                    double currentRatio, double debtToEquity,
                                    double roic, double upside) {
        int score = 40;
        if (revenueGrowth > 0) {
            double peg = pe / revenueGrowth;
            if      (peg > 0 && peg < 0.5)  score += 25;
            else if (peg >= 0.5 && peg < 1)  score += 18;
            else if (peg >= 1 && peg < 1.5)  score += 8;
            else if (peg >= 2)               score -= 10;
        }
        if      (revenueGrowth >= 20) score += 15;
        else if (revenueGrowth >= 10) score += 10;
        else if (revenueGrowth >= 5)  score += 5;
        else if (revenueGrowth < 0)   score -= 15;
        if      (netMargin >= 15) score += 10;
        else if (netMargin >= 8)  score += 5;
        else if (netMargin < 3)   score -= 8;
        if      (currentRatio >= 2) score += 5;
        else if (currentRatio < 1)  score -= 5;
        if      (debtToEquity < 0.5) score += 5;
        else if (debtToEquity > 1.5) score -= 8;
        if      (roic >= 15) score += 8;
        else if (roic >= 10) score += 4;
        return Math.min(100, Math.max(0, score));
    }

    private int calculateGrahamScore(double pe, double pb, double upside,
                                     double currentRatio, double debtToEquity,
                                     double dy, double netMargin) {
        int score = 40;
        if      (upside >= 30) score += 25;
        else if (upside >= 20) score += 18;
        else if (upside >= 10) score += 10;
        else if (upside < 0)   score -= 15;
        if      (pe > 0 && pe < 9)    score += 15;
        else if (pe >= 9 && pe < 15)   score += 8;
        else if (pe >= 15 && pe < 20)  score += 2;
        else if (pe >= 20)             score -= 10;
        if      (pb > 0 && pb < 1)     score += 12;
        else if (pb >= 1 && pb < 1.5)   score += 6;
        else if (pb >= 1.5 && pb < 2)   score += 2;
        else if (pb >= 2)               score -= 8;
        if      (currentRatio >= 2)    score += 8;
        else if (currentRatio >= 1.5)   score += 4;
        else if (currentRatio < 1)      score -= 10;
        if      (debtToEquity < 0.3) score += 8;
        else if (debtToEquity < 0.5) score += 4;
        else if (debtToEquity > 1.0) score -= 10;
        if      (dy >= 3) score += 5;
        else if (dy >= 1) score += 2;
        return Math.min(100, Math.max(0, score));
    }

    private int calculateClearInvestScore(double pe, double pb, double dy,
                                          double roe, double roic, double netMargin,
                                          double debtToEquity, double debtToEbitda,
                                          double currentRatio, double revenueGrowth,
                                          double upside, double grossMargin) {
        int score = 0;
        int pilares = 0;

        int pilar1 = 0;
        if      (roe >= 20) pilar1 += 12;
        else if (roe >= 12) pilar1 += 8;
        else if (roe >= 8)  pilar1 += 4;
        else                pilar1 -= 5;
        if      (roic >= 15) pilar1 += 8;
        else if (roic >= 10) pilar1 += 5;
        else if (roic >= 6)  pilar1 += 2;
        if (netMargin >= 10) pilar1 += 5;
        else if (netMargin > 0) pilar1 += 2;
        else pilar1 -= 5;
        score += pilar1;
        if (pilar1 >= 15) pilares++;

        int pilar2 = 0;
        if      (dy >= 8)  pilar2 += 15;
        else if (dy >= 6)  pilar2 += 12;
        else if (dy >= 4)  pilar2 += 7;
        else if (dy >= 2)  pilar2 += 3;
        else               pilar2 -= 8;
        if      (pe > 0 && pe < 10)    pilar2 += 10;
        else if (pe >= 10 && pe < 15)   pilar2 += 6;
        else if (pe >= 15 && pe < 20)   pilar2 += 2;
        else if (pe >= 20)              pilar2 -= 5;
        score += pilar2;
        if (pilar2 >= 15) pilares++;

        int pilar3 = 0;
        if      (upside >= 20) pilar3 += 10;
        else if (upside >= 10) pilar3 += 6;
        else if (upside >= 0)  pilar3 += 2;
        else                   pilar3 -= 5;
        if      (pb > 0 && pb < 1)     pilar3 += 5;
        else if (pb >= 1 && pb < 1.5)   pilar3 += 3;
        else if (pb >= 1.5 && pb < 2)   pilar3 += 1;
        else if (pb >= 2)               pilar3 -= 3;
        score += pilar3;
        if (pilar3 >= 10) pilares++;

        int pilar4 = 0;
        if      (revenueGrowth >= 10) pilar4 += 10;
        else if (revenueGrowth >= 5)  pilar4 += 6;
        else if (revenueGrowth >= 0)  pilar4 += 2;
        else                          pilar4 -= 5;
        if      (grossMargin >= 40) pilar4 += 5;
        else if (grossMargin >= 25) pilar4 += 3;
        score += pilar4;
        if (pilar4 >= 8) pilares++;

        int pilar5 = 0;
        if      (debtToEbitda > 0 && debtToEbitda < 2) pilar5 += 5;
        else if (debtToEbitda >= 2 && debtToEbitda < 3) pilar5 += 2;
        else if (debtToEbitda >= 3)                      pilar5 -= 4;
        if      (currentRatio >= 1.5) pilar5 += 4;
        else if (currentRatio >= 1)   pilar5 += 2;
        else                          pilar5 -= 3;
        if      (debtToEquity < 0.5) pilar5 += 3;
        else if (debtToEquity > 1.5) pilar5 -= 3;
        score += pilar5;
        if (pilar5 >= 8) pilares++;

        int pilar6 = 0;
        if      (grossMargin >= 40) pilar6 += 4;
        else if (grossMargin >= 25) pilar6 += 2;
        if      (roic >= 15) pilar6 += 4;
        else if (roic >= 10) pilar6 += 2;
        score += pilar6;
        if (pilar6 >= 6) pilares++;

        if      (pilares >= 5) score += 15;
        else if (pilares >= 4) score += 8;
        else if (pilares >= 3) score += 3;

        return Math.min(100, Math.max(0, score));
    }

    private int calculateDefaultScore(double pe, double pb, double upside,
                                      double netMargin, double roe, double dy) {
        int score = 50;
        if      (pe > 0 && pe < 8)    score += 15;
        else if (pe >= 8 && pe < 15)   score += 8;
        else if (pe >= 25)             score -= 10;
        if      (pb > 0 && pb < 1)    score += 8;
        else if (pb >= 1 && pb < 2)    score += 4;
        else if (pb > 4)               score -= 5;
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
