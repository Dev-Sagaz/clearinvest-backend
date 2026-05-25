package com.clearinvest.backend.model;
import lombok.Data;
@Data
public class StockAnalysis {
    private String ticker;
    private String companyName;
    private String market;
    private double currentPrice;
    private double fairPrice;
    private double upsidePercent;
    private int score;
    private String recommendation;
    private double peRatio;
    private double roe;
    private double debtToEbitda;
    private double netMargin;
    private double roic;
    private double debtToEquity;
    private double currentRatio;
    private double pbRatio;
    private double dividendYield;
    private double profitGrowth3y;
}
