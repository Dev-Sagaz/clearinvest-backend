package com.clearinvest.backend.service;

import com.clearinvest.backend.client.CoinGeckoClient;
import com.clearinvest.backend.model.CryptoAnalysis;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    private final CoinGeckoClient coinGeckoClient;

    public CryptoService(CoinGeckoClient coinGeckoClient) {
        this.coinGeckoClient = coinGeckoClient;
    }

    public CryptoAnalysis analyze(String symbol) {
        String coinId = resolveCoinId(symbol);

        JSONObject data = coinGeckoClient.getCoinData(coinId);
        JSONObject marketData = data.optJSONObject("market_data");

        CryptoAnalysis analysis = new CryptoAnalysis();
        analysis.setId(coinId);
        analysis.setSymbol(data.optString("symbol", symbol).toUpperCase());
        analysis.setName(data.optString("name", symbol));
        analysis.setImage(data.optJSONObject("image") != null
                ? data.optJSONObject("image").optString("large", "") : "");

        // Preço
        double price = getUsd(marketData, "current_price");
        analysis.setCurrentPrice(price);
        analysis.setPriceChange24h(getUsd(marketData, "price_change_24h"));
        analysis.setPriceChangePercent24h(marketData.optDouble("price_change_percentage_24h", 0));
        analysis.setPriceChangePercent7d(marketData.optDouble("price_change_percentage_7d", 0));
        analysis.setPriceChangePercent30d(marketData.optDouble("price_change_percentage_30d", 0));
        analysis.setAth(getUsd(marketData, "ath"));
        analysis.setAtl(getUsd(marketData, "atl"));
        analysis.setAthChangePercent(marketData.optDouble("ath_change_percentage", 0));

        // Mercado
        analysis.setMarketCap(getUsd(marketData, "market_cap"));
        analysis.setMarketCapRank(data.optLong("market_cap_rank", 0));
        analysis.setVolume24h(getUsd(marketData, "total_volume"));
        analysis.setCirculatingSupply(marketData.optDouble("circulating_supply", 0));
        analysis.setTotalSupply(marketData.optDouble("total_supply", 0));
        analysis.setMaxSupply(marketData.optDouble("max_supply", 0));

        // Indicadores Técnicos
        try {
            JSONArray ohlc = coinGeckoClient.getOhlcData(coinId, 200);
            double[] closes = extractCloses(ohlc);

            // RSI com Wilder's Smoothing
            double rsi = calculateRSI(closes, 14);
            analysis.setRsi14(round2(rsi));
            analysis.setSignal(rsi > 70 ? "Sobrecomprado" : rsi < 30 ? "Sobrevendido" : "Neutro");

            // Médias Móveis
            double ma20  = calculateMA(closes, 20);
            double ma50  = calculateMA(closes, 50);
            double ma200 = calculateMA(closes, Math.min(200, closes.length));
            analysis.setMa20(round2(ma20));
            analysis.setMa50(round2(ma50));
            analysis.setMa200(round2(ma200));
            analysis.setTrend(price > ma50 && ma50 > ma200 ? "Alta"
                    : price < ma50 && ma50 < ma200 ? "Baixa" : "Lateral");

            // MACD
            double ema12 = calculateEMA(closes, 12);
            double ema26 = calculateEMA(closes, 26);
            double macdLine = ema12 - ema26;

            // Signal line: EMA9 dos últimos valores de MACD
            double[] macdHistory = buildMacdHistory(closes, 12, 26, 9);
            double macdSignal = calculateEMA(macdHistory, 9);
            double macdHistogram = macdLine - macdSignal;

            analysis.setMacdLine(round2(macdLine));
            analysis.setMacdSignal(round2(macdSignal));
            analysis.setMacdHistogram(round2(macdHistogram));
            analysis.setMacdTrend(macdHistogram > 0 ? "Bullish"
                    : macdHistogram < 0 ? "Bearish" : "Neutro");

            // Bollinger Bands (MA20 ± 2σ)
            double[] bb = calculateBollingerBands(closes, 20, 2.0);
            analysis.setBbUpper(round2(bb[0]));
            analysis.setBbMiddle(round2(bb[1]));
            analysis.setBbLower(round2(bb[2]));
            analysis.setBbSignal(price > bb[0] ? "Acima da banda"
                    : price < bb[2] ? "Abaixo da banda" : "Dentro");

        } catch (Exception e) {
            analysis.setSignal("Neutro");
            analysis.setTrend("Lateral");
            analysis.setMacdTrend("Neutro");
            analysis.setBbSignal("Dentro");
        }

        // Score e Recomendação
        int score = calculateScore(analysis);
        analysis.setScore(score);
        analysis.setRecommendation(score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar");

        return analysis;
    }

    // ─── Cálculos ────────────────────────────────────────────────────────────────

    /**
     * RSI com Wilder's Smoothing (método correto).
     * Primeira média = SMA do período; depois suavização exponencial.
     */
    private double calculateRSI(double[] closes, int period) {
        if (closes.length < period + 1) return 50;

        // Calcula as diferenças
        double[] deltas = new double[closes.length - 1];
        for (int i = 0; i < deltas.length; i++) {
            deltas[i] = closes[i + 1] - closes[i];
        }

        // Seed: SMA dos primeiros `period` deltas
        double avgGain = 0, avgLoss = 0;
        for (int i = 0; i < period; i++) {
            if (deltas[i] > 0) avgGain += deltas[i];
            else avgLoss += Math.abs(deltas[i]);
        }
        avgGain /= period;
        avgLoss /= period;

        // Wilder's smoothing para o restante
        for (int i = period; i < deltas.length; i++) {
            double gain = deltas[i] > 0 ? deltas[i] : 0;
            double loss = deltas[i] < 0 ? Math.abs(deltas[i]) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * EMA (Exponential Moving Average).
     * Seed = SMA dos primeiros `period` valores.
     */
    private double calculateEMA(double[] values, int period) {
        if (values.length < period) return 0;
        double k = 2.0 / (period + 1);

        // Seed: SMA dos primeiros `period` valores
        double ema = 0;
        for (int i = 0; i < period; i++) ema += values[i];
        ema /= period;

        // EMA para o restante
        for (int i = period; i < values.length; i++) {
            ema = values[i] * k + ema * (1 - k);
        }
        return ema;
    }

    /**
     * Constrói o histórico de valores MACD (EMA12 - EMA26)
     * para depois calcular a Signal Line (EMA9 do MACD).
     */
    private double[] buildMacdHistory(double[] closes, int fast, int slow, int signal) {
        int size = closes.length - slow + 1;
        if (size <= 0) return new double[]{0};

        double[] macdValues = new double[size];
        for (int i = 0; i < size; i++) {
            double[] slice = java.util.Arrays.copyOfRange(closes, i, i + slow);
            double emaFast = calculateEMA(slice, fast);
            double emaSlow = calculateEMA(slice, slow);
            macdValues[i] = emaFast - emaSlow;
        }
        return macdValues;
    }

    /**
     * SMA simples.
     */
    private double calculateMA(double[] closes, int period) {
        if (closes.length < period) return 0;
        double sum = 0;
        for (int i = closes.length - period; i < closes.length; i++) sum += closes[i];
        return sum / period;
    }

    /**
     * Bollinger Bands: [upper, middle, lower]
     * middle = MA(period), upper = middle + mult*σ, lower = middle - mult*σ
     */
    private double[] calculateBollingerBands(double[] closes, int period, double mult) {
        if (closes.length < period) return new double[]{0, 0, 0};
        double middle = calculateMA(closes, period);
        double variance = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            variance += Math.pow(closes[i] - middle, 2);
        }
        double stdDev = Math.sqrt(variance / period);
        return new double[]{middle + mult * stdDev, middle, middle - mult * stdDev};
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private double[] extractCloses(JSONArray ohlc) {
        double[] closes = new double[ohlc.length()];
        for (int i = 0; i < ohlc.length(); i++) {
            JSONArray candle = ohlc.getJSONArray(i);
            closes[i] = candle.getDouble(4); // [timestamp, open, high, low, close]
        }
        return closes;
    }

    private int calculateScore(CryptoAnalysis a) {
        int score = 50;

        // RSI
        if (a.getRsi14() > 0 && a.getRsi14() < 30)       score += 20; // sobrevendido = oportunidade
        else if (a.getRsi14() >= 30 && a.getRsi14() < 50) score += 10;
        else if (a.getRsi14() > 70)                        score -= 15; // sobrecomprado = risco

        // Tendência por médias móveis
        if ("Alta".equals(a.getTrend()))   score += 15;
        else if ("Baixa".equals(a.getTrend())) score -= 10;

        // MACD
        if ("Bullish".equals(a.getMacdTrend()))  score += 10;
        else if ("Bearish".equals(a.getMacdTrend())) score -= 10;

        // Bollinger Bands
        if ("Abaixo da banda".equals(a.getBbSignal())) score += 10; // possível reversão
        else if ("Acima da banda".equals(a.getBbSignal())) score -= 5;

        // Variação 30d
        if (a.getPriceChangePercent30d() < -30) score += 15; // oversold
        else if (a.getPriceChangePercent30d() > 50) score -= 10;

        // Market cap rank
        if (a.getMarketCapRank() <= 10)      score += 10;
        else if (a.getMarketCapRank() <= 50) score += 5;

        return Math.min(100, Math.max(0, score));
    }

    private String resolveCoinId(String symbol) {
        return switch (symbol.toLowerCase()) {
            case "btc", "bitcoin"    -> "bitcoin";
            case "eth", "ethereum"   -> "ethereum";
            case "sol", "solana"     -> "solana";
            case "bnb"               -> "binancecoin";
            case "xrp", "ripple"     -> "ripple";
            case "ada", "cardano"    -> "cardano";
            case "doge", "dogecoin"  -> "dogecoin";
            case "dot", "polkadot"   -> "polkadot";
            case "avax", "avalanche" -> "avalanche-2";
            case "link", "chainlink" -> "chainlink";
            case "ltc", "litecoin"   -> "litecoin";
            case "uni", "uniswap"    -> "uniswap";
            case "atom", "cosmos"    -> "cosmos";
            case "matic", "polygon"  -> "matic-network";
            case "near"              -> "near";
            case "sui"               -> "sui";
            case "pepe"              -> "pepe";
            default                  -> symbol.toLowerCase();
        };
    }

    private double getUsd(JSONObject obj, String key) {
        if (obj == null) return 0;
        JSONObject inner = obj.optJSONObject(key);
        if (inner != null) return inner.optDouble("usd", 0);
        return obj.optDouble(key, 0);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
