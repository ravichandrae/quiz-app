package org.schoolmela.quiz.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param timeZone time zone for dates and times written in exported reports */
@ConfigurationProperties("app.reports")
public record ReportProperties(@DefaultValue("Asia/Kolkata") ZoneId timeZone) {
}
