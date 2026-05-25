package com.clearinvest.backend.controller;

import com.clearinvest.backend.model.StockAnalysis;
import com.clearinvest.backend.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis/{ticker}")
    public StockAnalysis analyze(@PathVariable String ticker) {
        return analysisService.analyze(ticker);
    }
}
