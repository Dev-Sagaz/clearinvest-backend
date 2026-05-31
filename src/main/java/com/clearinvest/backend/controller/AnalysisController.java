package com.clearinvest.backend.controller;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.client.FundamentusClient;
import com.clearinvest.backend.client.YahooFinanceClient;
import com.clearinvest.backend.model.StockAnalysis;
import com.clearinvest.backend.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final BrapiClient brapiClient;
    private final FundamentusClient fundamentusClient;
    private final YahooFinanceClient yahooFinanceClient;

    public AnalysisController(AnalysisService analysisService,
                              BrapiClient brapiClient,
                              FundamentusClient fundamentusClient,
                              YahooFinanceClient yahooFinanceClient) {
        this.analysisService = analysisService;
        this.brapiClient = brapiClient;
        this.fundamentusClient = fundamentusClient;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @GetMapping("/analysis/{ticker}")
    public StockAnalysis analyze(@PathVariable String ticker) {
        return analysisService.analyze(ticker);
    }

    @GetMapping("/debug/{ticker}")
    public String debug(@PathVariable String ticker) {
        return brapiClient.getQuote(ticker);
    }

    @GetMapping("/debug/fundamentus/{ticker}")
    public String debugFundamentus(@PathVariable String ticker) {
        Map<String, Double> data = fundamentusClient.getIndicators(ticker);
        return data.toString();
    }

    @GetMapping("/debug/yahoo/{ticker}")
    public String debugYahoo(@PathVariable String ticker) {
        return yahooFinanceClient.getQuoteSummary(ticker);
    }
}