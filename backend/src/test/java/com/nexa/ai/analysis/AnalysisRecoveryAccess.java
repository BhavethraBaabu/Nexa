package com.nexa.ai.analysis;

import org.springframework.context.ApplicationContext;

/** Lets tests outside the package trigger the package-private recovery sweep. */
public record AnalysisRecoveryAccess(ApplicationContext context) {

    public void failStaleAnalyses() {
        context.getBean(AnalysisRecovery.class).failStaleAnalyses();
    }
}
