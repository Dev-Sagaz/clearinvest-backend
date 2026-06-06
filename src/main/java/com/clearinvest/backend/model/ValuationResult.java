package com.clearinvest.backend.model;

import lombok.Data;
import java.util.List;

@Data
public class ValuationResult {

    private List<ValuationMethod> methods;
    private double precoAtual;
    private double rangeMin;
    private double rangeMax;
    private double mediaPonderada;
    private String consenso; // "COMPRAR", "NEUTRO", "EVITAR"
    private int metodosFavoraveis; // quantos métodos dizem subvalorizada

    @Data
    public static class ValuationMethod {
        private String nome;
        private double precoJusto;
        private String status; // "Subvalorizada", "Sobrevalorizada", "Neutro"
        private String descricao;
        private boolean disponivel; // false se faltar dado
    }
}