package com.clearinvest.backend.model;

import lombok.Data;

@Data
public class StockAnalysis {

    // Identificação
    private String ticker;
    private String companyName;
    private String market;
    private String mode;

    // Preço e valuation
    private double currentPrice;
    private double fairPrice;
    private double upsidePercent;

    // Recomendação
    private int score;
    private String recommendation;

    // Múltiplos
    private double peRatio;
    private double pbRatio;
    private double dividendYield;
    private double psr;

    // DRE
    private double revenue;
    private double grossProfit;
    private double operatingIncome;
    private double netIncome;
    private double ebitda;
    private double grossMargin;
    private double ebitMargin;
    private double netMargin;
    private double revenueGrowth5y;

    // Balanço
    private double totalAssets;
    private double totalLiabilities;
    private double totalEquity;
    private double debtToEquity;
    private double currentRatio;

    // DFC
    private double operatingCashFlow;
    private double freeCashFlow;
    private double capitalExpenditure;

    // Rentabilidade
    private double roe;
    private double roic;
    private double debtToEbitda;
    private double profitGrowth3y;

    // Indicadores adicionais
    private double evEbit;
    private double pEbit;
    private double pAtivos;
    private double giroAtivos;
    private double pCapGiro;
    private double ebitAtivo;
    private double min52w;
    private double max52w;
    private double volumeMedio;
    private double valorMercado;
    private double valorFirma;

    // Valuation Robusto
    private ValuationResult valuationRobusto;
}