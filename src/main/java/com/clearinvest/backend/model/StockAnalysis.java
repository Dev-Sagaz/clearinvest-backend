package com.clearinvest.backend.model;

import lombok.Data;

@Data
public class StockAnalysis {
    // Dados básicos
    private String ticker;
    private String companyName;
    private String market;
    private double currentPrice;
    private double fairPrice;
    private double upsidePercent;
    private int score;
    private String recommendation;

    // Indicadores de mercado
    private double peRatio;
    private double pbRatio;
    private double dividendYield;

    // DRE (Demonstração de Resultado)
    private double revenue;
    private double grossProfit;
    private double operatingIncome;
    private double netIncome;
    private double netMargin;
    private double grossMargin;
    private double ebitda;

    // Balanço Patrimonial
    private double totalAssets;
    private double totalLiabilities;
    private double totalEquity;
    private double currentRatio;
    private double debtToEquity;

    // DFC (Demonstração de Fluxo de Caixa)
    private double operatingCashFlow;
    private double freeCashFlow;
    private double capitalExpenditure;

    // Indicadores de rentabilidade
    private double roe;
    private double roic;
    private double debtToEbitda;
    private double profitGrowth3y;
}