package com.clearinvest.backend.controller;

import com.clearinvest.backend.model.CryptoAnalysis;
import com.clearinvest.backend.service.CryptoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CryptoController {

    private final CryptoService cryptoService;

    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping("/crypto/{symbol}")
    public CryptoAnalysis analyze(@PathVariable String symbol) {
        return cryptoService.analyze(symbol);
    }
}