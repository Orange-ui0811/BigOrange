package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerStateTest {
    private Map<String,Object> action(Map<String,Object> changes) { return Map.of("intent","COLLECT","changes",changes,"positions",List.of()); }
    @Test void explicitCorrectionOverridesPriorLanguageAndKeepsExclusion() {
        var updated=CustomerState.update(Map.of("language","Python","allowDocker",false),action(Map.of("language",Map.of("value","Java","quote","换成Java"))),"换成Java","");
        assertThat(updated).containsEntry("language","Java").containsEntry("allowDocker",false);
    }
    @Test void profileCannotOverwriteCurrentConditions() {
        var act=Map.<String,Object>of("intent","COLLECT","profileDefaults",Map.of("language",Map.of("value","Python","quote","Python")));
        assertThat(CustomerState.update(Map.of("language","Java"),act,"你好","Python")).containsEntry("language","Java");
    }
    @Test void fabricatedQuoteCannotChangeConditions() {
        assertThatThrownBy(()->CustomerState.update(Map.of(),action(Map.of("language",Map.of("value","Java","quote","Java"))),"只要Python","")).hasMessage("UNSUPPORTED_CONDITION");
    }
    @Test void greetingDoesNotFailWhenModelRepeatsConditionsFromOldHistory() {
        var prior=Map.<String,Object>of("language","Java","experience","刚学","goal","学习","allowDocker",false);
        var action=Map.<String,Object>of("intent","CHAT","reply","你好！", "changes",Map.of(
            "language",Map.of("value","Java","quote","我刚学Java"),
            "goal",Map.of("value","学习","quote","学习技术"),
            "allowDocker",Map.of("value",true,"quote","可以使用Docker")));
        assertThat(CustomerState.update(prior,action,"你好","")).isEqualTo(prior);
    }
    @Test void chatAppliesOnlyIndividuallyGroundedChangesAndKeepsExclusions() {
        var action=Map.<String,Object>of("intent","CHAT","reply","好的", "changes",Map.of(
            "language",Map.of("value","Java","quote","换成Java"),
            "allowDocker",Map.of("value",true,"quote","允许Docker")));
        assertThat(CustomerState.update(Map.of("language","Python","allowDocker",false),action,"换成Java", ""))
            .containsEntry("language","Java").containsEntry("allowDocker",false);
    }
    @Test void sourceQuoteCannotSupportDifferentLanguage() {
        assertThatThrownBy(()->CustomerState.update(Map.of(),action(Map.of("language",Map.of("value","Java","quote","Python"))),"Python","")).hasMessage("UNSUPPORTED_LANGUAGE");
    }
    @Test void dailyTimeCannotBecomeCompletionDuration() {
        assertThatThrownBy(()->CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",2,"quote","每天2小时"))),"每天2小时","")).hasMessage("DAILY_TIME_IS_NOT_DURATION");
    }
    @Test void durationConversionRequiresActualEvidence() {
        assertThat(CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",14,"quote","两周以内"))),"两周以内","")).containsEntry("maxDurationDays",14);
        assertThatThrownBy(()->CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",90,"quote","两周以内"))),"两周以内","")).hasMessage("UNSUPPORTED_DURATION");
    }
    @Test void halfYearAndHalfMonthAreValidatedWithoutRelaxingTheUsersLimit() {
        assertThat(CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",180,"quote","半年内完成"))),"半年内完成","")).containsEntry("maxDurationDays",180);
        assertThat(CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",15,"quote","半个月"))),"半个月","")).containsEntry("maxDurationDays",15);
        assertThatThrownBy(()->CustomerState.update(Map.of(),action(Map.of("maxDurationDays",Map.of("value",365,"quote","半年"))),"半年","")).hasMessage("UNSUPPORTED_DURATION");
    }
    @Test void nullCannotSilentlyRelaxRestriction() {
        var change=new HashMap<String,Object>(); change.put("value",null); change.put("quote","继续推荐");
        assertThatThrownBy(()->CustomerState.update(Map.of("allowDocker",false),action(Map.of("allowDocker",change)),"继续推荐","")).hasMessage("UNCONFIRMED_RELAXATION");
    }
    @Test void onlyOneMissingFieldIsAskedAndNoRepeatAfterCompletion() {
        assertThat(CustomerState.followUp(Map.of())).contains("学习");
        assertThat(CustomerState.followUp(Map.of("goal","学习"))).contains("基础");
        assertThat(CustomerState.followUp(Map.of("goal","学习","experience","新手","language","Java"))).isNull();
    }
    @Test void cancellingDockerCannotRemoveLanguageRestriction() {
        Map<String,Object> removal=new HashMap<>(); removal.put("value",null); removal.put("quote","取消Docker限制");
        assertThatThrownBy(()->CustomerState.update(Map.of("language","Java"),action(Map.of("language",removal)),"取消Docker限制","")).hasMessage("UNCONFIRMED_RELAXATION");
        assertThat(CustomerState.update(Map.of("allowDocker",false),action(Map.of("allowDocker",removal)),"取消Docker限制","")).doesNotContainKey("allowDocker");
    }
    @Test void negativeResourceQuoteCannotGrantPermission() {
        assertThatThrownBy(()->CustomerState.update(Map.of("allowDocker",false),action(Map.of("allowDocker",Map.of("value",true,"quote","不要Docker"))),"不要Docker","")).hasMessage("UNSUPPORTED_PERMISSION");
    }
    @Test void unknownPositionAndUnregisteredActionAreRejected() {
        assertThatThrownBy(()->CustomerState.positions(List.of(3),2)).hasMessage("UNKNOWN_PROJECT_POSITION");
        assertThatThrownBy(()->CustomerState.update(Map.of(),Map.of("intent","EXECUTE_SQL"),"","")).hasMessage("INVALID_INTENT");
    }
}
