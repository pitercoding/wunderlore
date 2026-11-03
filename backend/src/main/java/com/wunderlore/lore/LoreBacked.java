package com.wunderlore.lore;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a controller that depends on LoreDataService and says how its lore failures are
// reported. LoreUnavailableAdvice applies to every controller; this only supplies the wording,
// so a new lore-backed controller needs no change to the advice, and one without the annotation
// gets the generic wording.
// unavailableDetail: the public ProblemDetail detail of the 503.
// failure: the prefix of the DEBUG log line, written on the controller's own logger.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LoreBacked {

    String unavailableDetail();

    String failure();
}
