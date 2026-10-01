package org.schoolmela.quiz.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param answerGrace extra time after a question's deadline in which an answer still counts, to
 *                    allow for slow mobile networks
 */
@ConfigurationProperties("app.quiz")
public record QuizProperties(@DefaultValue("3s") Duration answerGrace) {
}
