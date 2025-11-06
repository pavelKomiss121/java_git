/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp170.DailySalesReport;
import ru.mentee.power.model.mp170.SalesPersonRanking;
import ru.mentee.power.repository.interfaces.WindowFunctionsRepository;

public class WindowFunctionsServiceImpl implements WindowFunctionsService {

    private final WindowFunctionsRepository repository;
    private final SalesAnalyticsProcessor processor;

    public WindowFunctionsServiceImpl(
            WindowFunctionsRepository repository, SalesAnalyticsProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    @Override
    public List<SalesPersonRanking> getRankedSalesPeopleByRegion() throws BusinessException {
        try {
            List<SalesPersonRanking> rankings = repository.executeRankingQuery();
            return processor.processWindowFunctionResults(rankings);
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения ранжированных продавцов", e);
        }
    }

    @Override
    public List<DailySalesReport> getDailySalesWithRunningTotals() throws BusinessException {
        try {
            List<DailySalesReport> reports = repository.calculateRunningTotals();
            return processor.processDailySalesReports(reports);
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения ежедневных продаж", e);
        }
    }

    @Override
    public List<SalesPersonRanking> getPerformanceVsRegionalAverage() throws BusinessException {
        try {
            List<SalesPersonRanking> rankings = repository.analyzeRegionalPerformance();
            processor.validateRankingData(rankings);
            return rankings;
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка анализа региональной производительности", e);
        }
    }

    @Override
    public List<SalesPersonRanking> getTopProductsWithMarketShare(Integer topLimit)
            throws BusinessException {
        try {
            List<SalesPersonRanking> rankings = repository.executeRankingQuery();
            return processor.calculateMarketShares(rankings, topLimit);
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения топ продуктов", e);
        }
    }

    @Override
    public List<SalesPersonRanking> getSalesPersonQuartiles(Integer quartileNumber)
            throws BusinessException {
        if (quartileNumber < 1 || quartileNumber > 4) {
            throw new BusinessException("Номер квартиля должен быть от 1 до 4");
        }
        try {
            List<SalesPersonRanking> rankings =
                    repository.generateQuartileDistribution(quartileNumber);
            processor.validateRankingData(rankings);
            return rankings;
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения квартилей", e);
        }
    }
}
