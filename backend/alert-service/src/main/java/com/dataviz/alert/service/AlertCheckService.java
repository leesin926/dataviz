package com.dataviz.alert.service;

/**
 * Scheduled alert check service that evaluates rules against data and creates AlertEvents
 */
public interface AlertCheckService {

    /**
     * Check all enabled rules and create alert events when thresholds are exceeded
     */
    void checkAllRules();

    /**
     * Check a specific rule
     */
    void checkRule(Long ruleId);
}
