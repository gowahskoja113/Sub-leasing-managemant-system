package com.sep490.slms2026.service;

import java.util.Map;

public interface BillingCronService {
    /**
     * Run daily billing sweep to handle reminders and late fees.
     * @return A map with execution statistics (e.g., reminded, overdueMarked, renotified).
     */
    Map<String, Integer> runDailySweep();
}
