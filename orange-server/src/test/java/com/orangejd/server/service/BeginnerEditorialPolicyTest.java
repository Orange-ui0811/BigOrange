package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeginnerEditorialPolicyTest {
    private final BeginnerEditorialPolicy policy = new BeginnerEditorialPolicy();

    @Test
    void acceptsAProjectThatCanQuicklyBecomeAShowcase() {
        assertTrue(policy.publishable(82,
                new BeginnerEditorialPolicy.Ratings(5, 5, 4, 4, 5)));
    }

    @Test
    void rejectsAnEasyToInstallSdkWithoutAVisibleOutcome() {
        assertFalse(policy.publishable(95,
                new BeginnerEditorialPolicy.Ratings(4, 2, 3, 2, 2)));
    }

    @Test
    void rejectsAProjectThatIsHardToUnderstandEvenWhenOtherRatingsAreHigh() {
        assertFalse(policy.publishable(90,
                new BeginnerEditorialPolicy.Ratings(5, 5, 5, 3, 5)));
    }

    @Test
    void calibratesAnAllPerfectResponseInsteadOfAcceptingModelEnthusiasm() {
        BeginnerEditorialPolicy.Ratings calibrated = policy.calibrate(
                new BeginnerEditorialPolicy.Ratings(5, 5, 5, 5, 5),
                new BeginnerEditorialPolicy.Signals(90, true, "SMALL",
                        false, false, false, false, false, false,
                        "visual web app template with a live demo"));

        assertEquals(new BeginnerEditorialPolicy.Ratings(5, 5, 4, 4, 4), calibrated);
        assertEquals(91, policy.score(calibrated));
    }

    @Test
    void rejectsLargeRepositoryEvenWhenItsProductIsBeginnerFriendly() {
        BeginnerEditorialPolicy.Ratings calibrated = policy.calibrate(
                new BeginnerEditorialPolicy.Ratings(5, 5, 5, 5, 5),
                new BeginnerEditorialPolicy.Signals(92, true, "LARGE",
                        false, false, false, false, false, false,
                        "visual creative coding framework with examples"));

        assertEquals(2, calibrated.scopeClarity());
        assertEquals(3, calibrated.extensionPotential());
        assertFalse(policy.publishable(92, calibrated));
    }

    @Test
    void limitsHeadlessLibraryWithoutStandaloneVisualOutcome() {
        BeginnerEditorialPolicy.Ratings calibrated = policy.calibrate(
                new BeginnerEditorialPolicy.Ratings(5, 5, 5, 5, 5),
                new BeginnerEditorialPolicy.Signals(86, true, "SMALL",
                        false, false, false, false, false, false,
                        "typescript sdk library for a protocol runtime"));

        assertEquals(3, calibrated.showcaseValue());
        assertEquals(3, calibrated.contentPotential());
        assertFalse(policy.publishable(86, calibrated));
    }
}
