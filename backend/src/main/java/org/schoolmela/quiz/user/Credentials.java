package org.schoolmela.quiz.user;

/** Validation rules shared by every request that carries a mobile number or PIN. */
public final class Credentials {

    public static final String MOBILE_PATTERN = "\\d{10}";
    public static final String MOBILE_MESSAGE = "Mobile number must be 10 digits";

    public static final String PIN_PATTERN = "\\d{4,6}";
    public static final String PIN_MESSAGE = "PIN must be 4 to 6 digits";

    private Credentials() {
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
