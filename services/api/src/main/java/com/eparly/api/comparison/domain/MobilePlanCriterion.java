package com.eparly.api.comparison.domain;

public enum MobilePlanCriterion {
    MIN_DATA_GB("minDataGb"),
    REQUIRES_5G("requires5G"),
    ROAMING_REQUIRED("roamingRequired"),
    ROAMING_COUNTRIES("roamingCountries"),
    MIN_ROAMING_DATA_GB("minRoamingDataGb"),
    MAX_COMMITMENT_MONTHS("maxCommitmentMonths"),
    ELIGIBILITY_RESTRICTIONS("eligibilityRestrictions");

    private final String code;

    MobilePlanCriterion(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MobilePlanCriterion fromCode(String code) {
        for (MobilePlanCriterion c : values()) {
            if (c.code.equals(code)) return c;
        }
        return null;
    }
}
