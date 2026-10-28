package com.eparly.api.comparison.domain;

public enum StreamingCriterion {
    MIN_SCREENS("minScreens"),
    MIN_VIDEO_QUALITY("minVideoQuality"),
    ADS_ACCEPTED("adsAccepted"),
    OFFLINE_DOWNLOAD_REQUIRED("offlineDownloadRequired"),
    MAX_COMMITMENT_MONTHS("maxCommitmentMonths"),
    REQUIRED_CONTENT("requiredContent");

    private final String code;

    StreamingCriterion(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static StreamingCriterion fromCode(String code) {
        for (StreamingCriterion c : values()) {
            if (c.code.equals(code)) return c;
        }
        return null;
    }
}
