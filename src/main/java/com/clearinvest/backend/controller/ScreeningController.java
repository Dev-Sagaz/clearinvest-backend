package com.clearinvest.backend.controller;

import com.clearinvest.backend.client.FundamentusClient;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScreeningController {

    private final FundamentusClient fundamentusClient;

    // 50 principais ações B3 por liquidez
    private static final List<String> B3_TICKERS = List.of(
            "PETR4", "VALE3", "ITUB4", "BBDC4", "WEGE3",
            "ABEV3", "BBAS3", "RENT3", "SUZB3", "RDOR3",
            "EGIE3", "TAEE11", "TRPL4", "CMIG4", "ENGI11",
            "VIVT3", "TIMS3", "CPLE6", "SBSP3", "SAPR11",
            "ITSA4", "BRAP4", "CSNA3", "GGBR4", "USIM5",
            "KLBN11", "DXCO3", "MRFG3", "BEEF3", "JBSS3",
            "LREN3", "MGLU3", "VVAR3", "AMER3", "SOMA3",
            "HAPV3", "GNDI3", "RDRD3", "FLRY3", "DASA3",
            "CSAN3", "PRIO3", "UGPA3", "BRDT3", "RRRP3",
            "BPAC11", "SANB11", "BRSR6", "TASA4", "CYRE3"
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

                double pe   = fund.getOrDefault("P/L", 0.0);
                double pb   = fund.getOrDefault("P/VP", 0.0);
                double dy   = fund.getOrDefault("Div. Yield", 0.0);
                double roe  = fund.getOrDefault("ROE", 0.0);
                double roic = fund.getOrDefault("ROIC", 0.0);
                double netMargin   = fund.getOrDefault("Marg. Líquida", 0.0);
                double currentRatio = fund.getOrDefault("Liquidez Corr", 0.0);
                double debtToEquity = fund.getOrDefault("Dív Líq / Patrim", 0.0);
                double revenueGrowth = fund.getOrDefault("Cres. Rec (5a)", 0.0);
                double price = fund.getOrDefault("Cotação", 0.0);

                boolean passes = switch (mode.toLowerCase()) {
                    case "barsi" -> passesBarsi(dy, pb, pe, roe, currentRatio, debtToEquity, netMargin);
                    case "buffett" -> passesBuffett(roe, roic, netMargin, debtToEquity, pe, revenueGrowth);
                    default -> passesDefault(pe, pb, dy, roe);
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
                    stock.put("mode", mode);
                    results.add(stock);
                }

                if (results.size() >= limit) break;

            } catch (Exception e) {
                System.err.println("Screening error for " + ticker + ": " + e.getMessage());
            }
        }

        // Ordena por DY (Barsi) ou ROE (Buffett/default)
        results.sort((a, b) -> {
            String sortField = mode.equalsIgnoreCase("barsi") ? "dividendYield" : "roe";
            double va = (double) a.getOrDefault(sortField, 0.0);
            double vb = (double) b.getOrDefault(sortField, 0.0);
            return Double.compare(vb, va);
        });

        return results;
    }

    // ── Critérios Barsi ──────────────────────────────────────────────────────
    private boolean passesBarsi(double dy, double pb, double pe, double roe,
                                double currentRatio, double debtToEquity, double netMargin) {
        return dy >= 5          // DY mínimo 5%
                && pb > 0 && pb <= 3  // P/VP razoável
                && pe > 0 && pe <= 20 // P/L razoável
                && roe >= 8           // ROE mínimo 8%
                && netMargin > 0;     // Empresa lucrativa
    }

    // ── Critérios Buffett ────────────────────────────────────────────────────
    private boolean passesBuffett(double roe, double roic, double netMargin,
                                  double debtToEquity, double pe, double revenueGrowth) {
        return roe >= 15          // ROE mínimo 15%
                && netMargin >= 10    // Margem líquida mínima 10%
                && debtToEquity < 1.0 // Dívida controlada
                && pe > 0 && pe <= 30 // Não muito caro
                && roic >= 10;        // ROIC mínimo 10%
    }

    // ── Critérios padrão ─────────────────────────────────────────────────────
    private boolean passesDefault(double pe, double pb, double dy, double roe) {
        return pe > 0 && pe <= 15
                && pb > 0 && pb <= 2
                && dy >= 3
                && roe >= 10;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
