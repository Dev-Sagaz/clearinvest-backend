package com.clearinvest.backend.controller;

import com.clearinvest.backend.client.BrapiClient;
import com.clearinvest.backend.model.StockAnalysis;
import com.clearinvest.backend.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final BrapiClient brapiClient;

    public AnalysisController(AnalysisService analysisService, BrapiClient brapiClient) {
        this.analysisService = analysisService;
        this.brapiClient = brapiClient;
    }

    @GetMapping("/analysis/{ticker}")
    public StockAnalysis analyze(@PathVariable String ticker) {
        return analysisService.analyze(ticker);
    }

    // Endpoint temporário para ver o JSON bruto da brapi
    @GetMapping("/debug/{ticker}")
    public String debug(@PathVariable String ticker) {
        return brapiClient.getQuote(ticker);
    }
}
