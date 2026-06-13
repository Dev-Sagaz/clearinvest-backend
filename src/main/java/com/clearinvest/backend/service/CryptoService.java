package com.clearinvest.backend.service;

import com.clearinvest.backend.client.BinanceClient;
import com.clearinvest.backend.model.CryptoAnalysis;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class CryptoService {

    private final BinanceClient binanceClient;

    private final ConcurrentHashMap<String, CryptoAnalysis> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 300_000;

    public CryptoService(BinanceClient binanceClient) {
        this.binanceClient = binanceClient;
    }

    public CryptoAnalysis analyze(String symbol) {
        String cacheKey = symbol.toLowerCase();

        try {
            if (cache.containsKey(cacheKey)) {
                long age = System.currentTimeMillis() - cacheTime.get(cacheKey);
                if (age < CACHE_TTL) return cache.get(cacheKey);
            }

            String binanceSymbol = resolveBinanceSymbol(symbol);

            JSONObject ticker = binanceClient.getTicker24h(binanceSymbol);

            CryptoAnalysis analysis = new CryptoAnalysis();
            analysis.setId(binanceSymbol.toLowerCase());
            analysis.setSymbol(binanceSymbol.toUpperCase());
            analysis.setName(resolveName(symbol));
            analysis.setImage("");

            double price = Double.parseDouble(ticker.optString("lastPrice", "0"));
            double priceChange24h = Double.parseDouble(ticker.optString("priceChange", "0"));
            double priceChangePercent24h = Double.parseDouble(ticker.optString("priceChangePercent", "0"));
            double high24h = Double.parseDouble(ticker.optString("highPrice", "0"));
            double low24h = Double.parseDouble(ticker.optString("lowPrice", "0"));
            double volume24h = Double.parseDouble(ticker.optString("quoteVolume", "0"));

            analysis.setCurrentPrice(round2(price));
            analysis.setPriceChange24h(round2(priceChange24h));
            analysis.setPriceChangePercent24h(round2(priceChangePercent24h));
            analysis.setPriceChangePercent7d(0);
            analysis.setPriceChangePercent30d(0);
            analysis.setAth(round2(high24h));
            analysis.setAtl(round2(low24h));
            analysis.setAthChangePercent(0);
            analysis.setMarketCap(0);
            analysis.setMarketCapRank(0);
            analysis.setVolume24h(round2(volume24h));
            analysis.setCirculatingSupply(0);
            analysis.setTotalSupply(0);
            analysis.setMaxSupply(0);

            try {
                JSONArray klines = binanceClient.getKlines(binanceSymbol, "1d", 200);
                double[] closes = extractClosesFromBinance(klines);

                double rsi = calculateRSI(closes, 14);
                analysis.setRsi14(round2(rsi));
                analysis.setSignal(rsi > 70 ? "Sobrecomprado" : rsi < 30 ? "Sobrevendido" : "Neutro");

                double ma20  = calculateMA(closes, 20);
                double ma50  = calculateMA(closes, 50);
                double ma200 = calculateMA(closes, Math.min(200, closes.length));
                analysis.setMa20(round2(ma20));
                analysis.setMa50(round2(ma50));
                analysis.setMa200(round2(ma200));
                analysis.setTrend(price > ma50 && ma50 > ma200 ? "Alta"
                        : price < ma50 && ma50 < ma200 ? "Baixa" : "Lateral");

                double ema12 = calculateEMA(closes, 12);
                double ema26 = calculateEMA(closes, 26);
                double macdLine = ema12 - ema26;
                double[] macdHistory = buildMacdHistory(closes, 12, 26, 9);
                double macdSignal = calculateEMA(macdHistory, 9);
                double macdHistogram = macdLine - macdSignal;
                analysis.setMacdLine(round2(macdLine));
                analysis.setMacdSignal(round2(macdSignal));
                analysis.setMacdHistogram(round2(macdHistogram));
                analysis.setMacdTrend(macdHistogram > 0 ? "Bullish" : macdHistogram < 0 ? "Bearish" : "Neutro");

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

            int score = calculateScore(analysis);
            analysis.setScore(score);
            analysis.setRecommendation(score >= 70 ? "Comprar" : score >= 50 ? "Neutro" : "Evitar");

            cache.put(cacheKey, analysis);
            cacheTime.put(cacheKey, System.currentTimeMillis());

            return analysis;

        } catch (Exception e) {
            if (cache.containsKey(cacheKey)) return cache.get(cacheKey);
            throw new RuntimeException("Erro ao buscar cripto: " + e.getMessage());
        }
    }

    private double[] extractClosesFromBinance(JSONArray klines) {
        double[] closes = new double[klines.length()];
        for (int i = 0; i < klines.length(); i++) {
            JSONArray candle = klines.getJSONArray(i);
            closes[i] = Double.parseDouble(candle.getString(4));
        }
        return closes;
    }

    private String resolveBinanceSymbol(String symbol) {
        return switch (symbol.toLowerCase()) {
            case "btc", "bitcoin"    -> "BTC";
            case "eth", "ethereum"   -> "ETH";
            case "sol", "solana"     -> "SOL";
            case "bnb"               -> "BNB";
            case "xrp", "ripple"     -> "XRP";
            case "ada", "cardano"    -> "ADA";
            case "doge", "dogecoin"  -> "DOGE";
            case "dot", "polkadot"   -> "DOT";
            case "avax", "avalanche" -> "AVAX";
            case "link", "chainlink" -> "LINK";
            case "ltc", "litecoin"   -> "LTC";
            case "matic", "polygon"  -> "MATIC";
            case "near"              -> "NEAR";
            case "sui"               -> "SUI";
            case "pepe"              -> "PEPE";
            default                  -> symbol.toUpperCase();
        };
    }

    private String resolveName(String symbol) {
        return switch (symbol.toLowerCase()) {
            case "btc", "bitcoin"    -> "Bitcoin";
            case "eth", "ethereum"   -> "Ethereum";
            case "sol", "solana"     -> "Solana";
            case "bnb"               -> "BNB";
            case "xrp", "ripple"     -> "XRP";
            case "ada", "cardano"    -> "Cardano";
            case "doge", "dogecoin"  -> "Dogecoin";
            case "dot", "polkadot"   -> "Polkadot";
            case "avax", "avalanche" -> "Avalanche";
            case "link", "chainlink" -> "Chainlink";
            case "ltc", "litecoin"   -> "Litecoin";
            case "matic", "polygon"  -> "Polygon";
            case "near"              -> "NEAR";
            case "sui"               -> "Sui";
            case "pepe"              -> "Pepe";
            default                  -> symbol.toUpperCase();
        };
    }
    private String resolveCoinGeckoId(String symbol) {
    return switch (symbol.toLowerCase()) {
        case "btc", "bitcoin"    -> "bitcoin";
        case "eth", "ethereum"   -> "ethereum";
        case "sol", "solana"     -> "solana";
        case "bnb"               -> "binancecoin";
        case "xrp", "ripple"     -> "ripple";
        case "ada", "cardano"    -> "cardano";
        case "doge", "dogecoin"  -> "dogecoin";
        case "avax", "avalanche" -> "avalanche-2";
        case "link", "chainlink" -> "chainlink";
        case "ltc", "litecoin"   -> "litecoin";
        case "near"              -> "near";
        case "sui"               -> "sui";
        case "pepe"              -> "pepe";
        default                  -> symbol.toLowerCase();
    };
}

    private double calculateRSI(double[] closes, int period) {
        if (closes.length < period + 1) return 50;
        double[] deltas = new double[closes.length - 1];
        for (int i = 0; i < deltas.length; i++) deltas[i] = closes[i + 1] - closes[i];
        double avgGain = 0, avgLoss = 0;
        for (int i = 0; i < period; i++) {
            if (deltas[i] > 0) avgGain += deltas[i];
            else avgLoss += Math.abs(deltas[i]);
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period; i < deltas.length; i++) {
            double gain = deltas[i] > 0 ? deltas[i] : 0;
            double loss = deltas[i] < 0 ? Math.abs(deltas[i]) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }
        if (avgLoss == 0) return 100;
        return 100 - (100 / (1 + avgGain / avgLoss));
    }

    private double calculateEMA(double[] values, int period) {
        if (values.length < period) return 0;
        double k = 2.0 / (period + 1);
        double ema = 0;
        for (int i = 0; i < period; i++) ema += values[i];
        ema /= period;
        for (int i = period; i < values.length; i++) ema = values[i] * k + ema * (1 - k);
        return ema;
    }

    private double[] buildMacdHistory(double[] closes, int fast, int slow, int signal) {
        int size = closes.length - slow + 1;
        if (size <= 0) return new double[]{0};
        double[] macdValues = new double[size];
        for (int i = 0; i < size; i++) {
            double[] slice = java.util.Arrays.copyOfRange(closes, i, i + slow);
            macdValues[i] = calculateEMA(slice, fast) - calculateEMA(slice, slow);
        }
        return macdValues;
    }

    private double calculateMA(double[] closes, int period) {
        if (closes.length < period) return 0;
        double sum = 0;
        for (int i = closes.length - period; i < closes.length; i++) sum += closes[i];
        return sum / period;
    }

    private double[] calculateBollingerBands(double[] closes, int period, double mult) {
        if (closes.length < period) return new double[]{0, 0, 0};
        double middle = calculateMA(closes, period);
        double variance = 0;
        for (int i = closes.length - period; i < closes.length; i++)
            variance += Math.pow(closes[i] - middle, 2);
        double stdDev = Math.sqrt(variance / period);
        return new double[]{middle + mult * stdDev, middle, middle - mult * stdDev};
    }

    private int calculateScore(CryptoAnalysis a) {
        int score = 50;
        if (a.getRsi14() > 0 && a.getRsi14() < 30) score += 20;
        else if (a.getRsi14() >= 30 && a.getRsi14() < 50) score += 10;
        else if (a.getRsi14() > 70) score -= 15;
        if ("Alta".equals(a.getTrend())) score += 15;
        else if ("Baixa".equals(a.getTrend())) score -= 10;
        if ("Bullish".equals(a.getMacdTrend())) score += 10;
        else if ("Bearish".equals(a.getMacdTrend())) score -= 10;
        if ("Abaixo da banda".equals(a.getBbSignal())) score += 10;
        else if ("Acima da banda".equals(a.getBbSignal())) score -= 5;
        if (a.getPriceChangePercent30d() < -30) score += 15;
        else if (a.getPriceChangePercent30d() > 50) score -= 10;
        if (a.getMarketCapRank() <= 10) score += 10;
        else if (a.getMarketCapRank() <= 50) score += 5;
        return Math.min(100, Math.max(0, score));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
    private void enrichWithCoinGecko(CryptoAnalysis analysis, String coinGeckoId) {
    try {
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + coinGeckoId 
            + "&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true";
        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
        String body = rt.getForObject(url, String.class);
        JSONObject json = new JSONObject(body).optJSONObject(coinGeckoId);
        if (json != null) {
            analysis.setMarketCap(json.optDouble("usd_market_cap", 0));
        }
    } catch (Exception ignored) {}
}
}
