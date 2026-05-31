package com.clearinvest.backend.client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

    @Component
    public class FundamentusClient {

        private static final String BASE_URL = "https://www.fundamentus.com.br/detalhes.php?papel=";

        public Map<String, Double> getIndicators(String ticker) {
            Map<String, Double> indicators = new HashMap<>();
            try {
                Document doc = Jsoup.connect(BASE_URL + ticker.toUpperCase())
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10_000)
                        .get();

                Elements rows = doc.select("table tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    for (int i = 0; i + 1 < cells.size(); i += 2) {
                        String label = cells.get(i).text().trim().replace("?", "") ;
                        String value = cells.get(i + 1).text().trim()
                                .replace(".", "")
                                .replace(",", ".")
                                .replace("%", "")
                                .trim();
                        if (!label.isEmpty() && !value.isEmpty()) {
                            try {
                                indicators.put(label, Double.parseDouble(value));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("FundamentusClient error for " + ticker + ": " + e.getMessage());
            }
            return indicators;
        }
    }

