package com.clearinvest.backend.controller;

import com.clearinvest.backend.client.FundamentusClient;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScreeningController {

    private final FundamentusClient fundamentusClient;

    private static final List<String> B3_TICKERS = List.of(
            "PETR4", "VALE3", "ITUB4", "BBDC4", "WEGE3",
            "ABEV3", "BBAS3", "RENT3", "SUZB3", "RDOR3",
            "EGIE3", "TAEE11", "TRPL4", "CMIG4", "ENGI11",
            "VIVT3", "TIMS3", "CPLE6", "SBSP3", "SAPR11",
            "ITSA4", "BRAP4", "CSNA3", "GGBR4", "USIM5",
            "KLBN11", "DXCO3", "MRFG3", "BEEF3", "JBSS3",
            "LREN3", "MGLU3", "SOMA3", "HAPV3", "FLRY3",
            "CSAN3", "PRIO3", "UGPA3", "BPAC11", "SANB11",
            "BRSR6", "CYRE3", "ELET3", "CPFE3", "EQTL3",
            "MULT3", "TEND3", "DIRR3", "EVEN3", "JHSF3"
    );

    public ScreeningController(FundamentusClient fundamentusClient) {
        this.fundamentusClient = fundamentusClient;
    }

    @GetMapping("/screening")
    public List<Map<String, Object>> screening(
            @RequestParam(required = false, defaultValue = "barsi") String mode,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<Map<String, Object>> results = new ArrayList<>();

        for (String ticker : B3_TICKERS) {
            try {
                Map<String, Double> fund = fundamentusClient.getIndicators(ticker);
                if (fund.isEmpty()) continue;

                double pe            = fund.getOrDefault("P/L", 0.0);
                double pb            = fund.getOrDefault("P/VP", 0.0);
                double dy            = fund.getOrDefault("Div. Yield", 0.0);
                double roe           = fund.getOrDefault("ROE", 0.0);
                double roic          = fund.getOrDefault("ROIC", 0.0);
                double netMargin     = fund.getOrDefault("Marg. Líquida", 0.0);
                double grossMargin   = fund.getOrDefault("Marg. Bruta", 0.0);
                double currentRatio  = fund.getOrDefault("Liquidez Corr", 0.0);
                double debtToEquity  = fund.getOrDefault("Dív Líq / Patrim", 0.0);
                double debtToEbitda  = fund.getOrDefault("EV / EBITDA", 0.0);
                double revenueGrowth = fund.getOrDefault("Cres. Rec (5a)", 0.0);
                double ebitOnAssets  = fund.getOrDefault("EBIT / Ativo", 0.0);
                double price         = fund.getOrDefault("Cotação", 0.0);

                boolean passes = switch (mode.toLowerCase()) {
                    case "barsi"       -> passesBarsi(dy, pb, pe, roe, currentRatio, debtToEquity, netMargin);
                    case "buffett"     -> passesBuffett(roe, roic, netMargin, debtToEquity, pe, revenueGrowth);
                    case "lynch"       -> passesLynch(pe, revenueGrowth, netMargin, roic);
                    case "graham"      -> passesGraham(pe, pb, currentRatio, debtToEquity, dy);
                    case "clearinvest" -> passesClearInvest(pe, pb, dy, roe, roic, netMargin, debtToEquity, currentRatio, revenueGrowth);
                    default            -> passesDefault(pe, pb, dy, roe);
                };

                if (passes) {
                    Map<String, Object> stock = new LinkedHashMap<>();
                    stock.put("ticker", ticker);
                    stock.put("price", price);
                    stock.put("peRatio", round2(pe));
                    stock.put("pbRatio", round2(pb));
                    stock.put("dividendYield", round2(dy));
                    stock.put("roe", round2(roe));
                    stock.put("roic", round2(roic));
                    stock.put("netMargin", round2(netMargin));
                    stock.put("currentRatio", round2(currentRatio));
                    stock.put("debtToEquity", round2(debtToEquity));
                    stock.put("revenueGrowth", round2(revenueGrowth));
                    stock.put("mode", mode);
                    results.add(stock);
                }

                if (results.size() >= limit) break;

            } catch (Exception e) {
                System.err.println("Screening error for " + ticker + ": " + e.getMessage());
            }
        }

        String sortField = switch (mode.toLowerCase()) {
            case "barsi"       -> "dividendYield";
            case "buffett"     -> "roe";
            case "lynch"       -> "revenueGrowth";
            case "graham"      -> "pbRatio";
            case "clearinvest" -> "roe";
            default            -> "dividendYield";
        };

        results.sort((a, b) -> {
            double va = (double) a.getOrDefault(sortField, 0.0);
            double vb = (double) b.getOrDefault(sortField, 0.0);
            return mode.equalsIgnoreCase("graham")
                    ? Double.compare(va, vb)
                    : Double.compare(vb, va);
        });

        return results;
    }

    private boolean passesBarsi(double dy, double pb, double pe, double roe,
                                double currentRatio, double debtToEquity, double netMargin) {
        return dy >= 5 && pb > 0 && pb <= 3 && pe > 0 && pe <= 20 && roe >= 8 && netMargin > 0;
    }

    private boolean passesBuffett(double roe, double roic, double netMargin,
                                  double debtToEquity, double pe, double revenueGrowth) {
        return roe >= 15 && netMargin >= 10 && debtToEquity < 1.0 && pe > 0 && pe <= 30 && roic >= 10;
    }

    private boolean passesLynch(double pe, double revenueGrowth, double netMargin, double roic) {
        return revenueGrowth >= 5 && pe > 0 && netMargin >= 5 && roic >= 8;
    }

    private boolean passesGraham(double pe, double pb, double currentRatio,
                                 double debtToEquity, double dy) {
        return pe > 0 && pe < 15 && pb > 0 && pb < 1.5 && currentRatio >= 1.5 && debtToEquity < 0.5 && dy >= 2;
    }

    private boolean passesClearInvest(double pe, double pb, double dy, double roe,
                                      double roic, double netMargin, double debtToEquity,
                                      double currentRatio, double revenueGrowth) {
        int pilares = 0;
        if (roe >= 10 && roic >= 8 && netMargin > 0) pilares++;
        if (dy >= 4 && pe > 0 && pe < 18)            pilares++;
        if (pb > 0 && pb < 2)                         pilares++;
        if (revenueGrowth >= 5)                       pilares++;
        if (currentRatio >= 1 && debtToEquity < 1.0) pilares++;
        return pilares >= 3;
    }

    private boolean passesDefault(double pe, double pb, double dy, double roe) {
        return pe > 0 && pe <= 15 && pb > 0 && pb <= 2 && dy >= 3 && roe >= 10;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}