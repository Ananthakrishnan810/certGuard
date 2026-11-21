package com.certguard.web;

import com.certguard.dto.DashboardSummary;
import com.certguard.service.CurrentUser;
import com.certguard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final CurrentUser me;

    public DashboardController(DashboardService dashboard, CurrentUser me) {
        this.dashboard = dashboard;
        this.me = me;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboard.summary(me.id());
    }
}
