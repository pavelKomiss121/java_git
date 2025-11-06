/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;

public class SalesAnalyticsProcessor {

    /**
     * Обрабатывает результаты оконных функций для продавцов.
     * Выполняет дополнительную валидацию и вычисления.
     *
     * @param rankings список ранжированных продавцов
     * @return обработанный список с дополнительными вычислениями
     */
    public List<SalesPersonRanking> processWindowFunctionResults(
            List<SalesPersonRanking> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        // Вычисляем общую сумму продаж для расчета доли рынка
        BigDecimal totalSales =
                rankings.stream()
                        .map(SalesPersonRanking::getTotalSales)
                        .filter(sales -> sales != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Обновляем долю рынка для каждого продавца
        return rankings.stream()
                .map(
                        ranking -> {
                            if (ranking.getTotalSales() != null
                                    && totalSales.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal marketShare =
                                        ranking.getTotalSales()
                                                .divide(totalSales, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100));
                                return SalesPersonRanking.builder()
                                        .id(ranking.getId())
                                        .name(ranking.getName())
                                        .regionName(ranking.getRegionName())
                                        .totalSales(ranking.getTotalSales())
                                        .regionRank(ranking.getRegionRank())
                                        .denseRank(ranking.getDenseRank())
                                        .rowNumber(ranking.getRowNumber())
                                        .marketSharePercent(marketShare)
                                        .build();
                            }
                            return ranking;
                        })
                .collect(Collectors.toList());
    }

    /**
     * Обрабатывает результаты оконных функций для ежедневных отчетов.
     * Вычисляет процент роста и другие метрики.
     *
     * @param reports список ежедневных отчетов
     * @return обработанный список с вычисленными метриками роста
     */
    public List<DailySalesReport> processDailySalesReports(List<DailySalesReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }

        List<DailySalesReport> processedReports = new java.util.ArrayList<>();
        DailySalesReport previousReport = null;

        for (DailySalesReport report : reports) {
            BigDecimal growthPercent = BigDecimal.ZERO;

            // Вычисляем процент роста относительно предыдущего дня
            if (previousReport != null
                    && previousReport.getDailySales() != null
                    && report.getDailySales() != null
                    && previousReport.getDailySales().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal difference =
                        report.getDailySales().subtract(previousReport.getDailySales());
                growthPercent =
                        difference
                                .divide(previousReport.getDailySales(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
            }

            DailySalesReport processedReport =
                    DailySalesReport.builder()
                            .saleDate(report.getSaleDate())
                            .dailySales(report.getDailySales())
                            .cumulativeSales(report.getCumulativeSales())
                            .transactionCount(report.getTransactionCount())
                            .avgTransactionAmount(report.getAvgTransactionAmount())
                            .growthPercent(growthPercent)
                            .build();

            processedReports.add(processedReport);
            previousReport = report;
        }

        return processedReports;
    }

    /**
     * Валидирует данные ранжирования.
     * Проверяет корректность рангов и данных.
     *
     * @param rankings список ранжированных продавцов
     * @throws IllegalArgumentException если данные некорректны
     */
    public void validateRankingData(List<SalesPersonRanking> rankings) {
        if (rankings == null) {
            throw new IllegalArgumentException("Список ранжирования не может быть null");
        }

        for (SalesPersonRanking ranking : rankings) {
            if (ranking.getRegionRank() != null
                    && ranking.getDenseRank() != null
                    && ranking.getRegionRank() < ranking.getDenseRank()) {
                throw new IllegalArgumentException(
                        "RANK не может быть меньше DENSE_RANK для одного продавца");
            }

            if (ranking.getRowNumber() != null
                    && ranking.getRegionRank() != null
                    && ranking.getRowNumber() < ranking.getRegionRank()) {
                throw new IllegalArgumentException(
                        "ROW_NUMBER не может быть меньше RANK для одного продавца");
            }

            if (ranking.getTotalSales() != null
                    && ranking.getTotalSales().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Сумма продаж не может быть отрицательной");
            }
        }
    }

    /**
     * Вычисляет долю рынка для списка продавцов и возвращает топ.
     *
     * @param rankings список ранжированных продавцов
     * @param topLimit количество топ продавцов для возврата
     * @return список топ продавцов с вычисленной долей рынка
     */
    public List<SalesPersonRanking> calculateMarketShares(
            List<SalesPersonRanking> rankings, Integer topLimit) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        // Обрабатываем результаты для вычисления доли рынка
        List<SalesPersonRanking> processed = processWindowFunctionResults(rankings);

        // Сортируем по сумме продаж и берем топ
        return processed.stream()
                .sorted(
                        (r1, r2) -> {
                            if (r1.getTotalSales() == null && r2.getTotalSales() == null) {
                                return 0;
                            }
                            if (r1.getTotalSales() == null) {
                                return 1;
                            }
                            if (r2.getTotalSales() == null) {
                                return -1;
                            }
                            return r2.getTotalSales().compareTo(r1.getTotalSales());
                        })
                .limit(topLimit != null ? topLimit : processed.size())
                .collect(Collectors.toList());
    }
}
