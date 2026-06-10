package com.clearinvest.backend.model;

import lombok.Data;

@Data
public class CryptoAnalysis {
    // Identificação
    private String id;
    private String symbol;
    private String name;
    private String image;

    // Preço
    private double currentPrice;
    private double priceChange24h;
    private double priceChangePercent24h;
    private double priceChangePercent7d;
    private double priceChangePercent30d;
    private double ath;
    private double atl;
    private double athChangePercent;

    // Mercado
    private double marketCap;
    private long marketCapRank;
    private double volume24h;
    private double circulatingSupply;
    private double totalSupply;
    private double maxSupply;

    // Indicadores Técnicos
    private double rsi14;
    private double ma20;
    private double ma50;
    private double ma200;
    private String signal;   // "Sobrecomprado", "Neutro", "Sobrevendido"
    private String trend;    // "Alta", "Lateral", "Baixa"

    // MACD
    private double macdLine;       // EMA12 - EMA26
    private double macdSignal;     // EMA9 do macdLine
    private double macdHistogram;  // macdLine - macdSignal
    private String macdTrend;      // "Bullish", "Bearish", "Neutro"

    // Bollinger Bands
    private double bbUpper;   // MA20 + 2σ
    private double bbMiddle;  // MA20
    private double bbLower;   // MA20 - 2σ
    private String bbSignal;  // "Abaixo da banda", "Dentro", "Acima da banda"

    // Score e Recomendação
    private int score;
    private String recommendation;
}
