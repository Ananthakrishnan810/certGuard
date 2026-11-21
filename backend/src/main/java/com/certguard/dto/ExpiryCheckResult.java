package com.certguard.dto;

public record ExpiryCheckResult(int checked, int statusChanged, int sent, int failed, int skippedDisabled) {}
