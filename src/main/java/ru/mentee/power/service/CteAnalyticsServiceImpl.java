/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.service;

import java.util.List;
import ru.mentee.power.exception.BusinessException;
import ru.mentee.power.exception.DataAccessException;
import ru.mentee.power.model.mp171.AbcAnalysisReport;
import ru.mentee.power.model.mp171.CategoryHierarchyReport;
import ru.mentee.power.model.mp171.CohortAnalysisReport;
import ru.mentee.power.model.mp171.CustomerSegmentReport;
import ru.mentee.power.model.mp171.ProductTrendReport;
import ru.mentee.power.repository.interfaces.CteAnalyticsRepository;

/**
 * Реализация сервиса для работы с CTE аналитикой.
 * Оркестрирует вызовы репозитория и обрабатывает бизнес-логику.
 */
public class CteAnalyticsServiceImpl implements CteAnalyticsService {

    private CteAnalyticsRepository repository;

    public CteAnalyticsServiceImpl(CteAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CustomerSegmentReport> getCustomerSegmentationReport() throws BusinessException {
        try {
            return repository.executeMultipleCte();
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения отчета о сегментации клиентов", e);
        }
    }

    @Override
    public List<ProductTrendReport> getProductTrendsReport() throws BusinessException {
        try {
            return repository.performProductTrendsAnalysis();
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения отчета о трендах продуктов", e);
        }
    }

    @Override
    public List<CategoryHierarchyReport> getCategoryHierarchyReport() throws BusinessException {
        try {
            return repository.executeRecursiveCte();
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения отчета об иерархии категорий", e);
        }
    }

    @Override
    public List<CohortAnalysisReport> getCohortAnalysisReport() throws BusinessException {
        try {
            return repository.performCohortAnalysis();
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения отчета о когортном анализе", e);
        }
    }

    @Override
    public List<AbcAnalysisReport> getAbcAnalysisReport() throws BusinessException {
        try {
            return repository.performAbcAnalysis();
        } catch (DataAccessException e) {
            throw new BusinessException("Ошибка получения отчета ABC анализа", e);
        }
    }
}
