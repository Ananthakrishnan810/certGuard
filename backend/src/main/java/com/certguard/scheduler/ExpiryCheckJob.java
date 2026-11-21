package com.certguard.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiryCheckJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryCheckJob.class);

    private final ExpiryCheckService service;

    public ExpiryCheckJob(ExpiryCheckService service) {
        this.service = service;
    }

    /** The daily reminder run. Schedule is EXPIRY_CHECK_CRON (default 08:00 every day). */
    @Scheduled(cron = "${app.expiry-check.cron}", zone = "${app.timezone}")
    public void dailyCheck() {
        log.info("Scheduled expiry check starting");
        service.run(null);
    }

    /** Keep statuses correct even between daily runs (no email). */
    @Scheduled(cron = "0 5 * * * *", zone = "${app.timezone}")
    public void hourlyStatusRefresh() {
        service.refreshStatuses();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int changed = service.refreshStatuses();
        log.info("Startup status refresh: {} certificate(s) changed status", changed);
    }
}
