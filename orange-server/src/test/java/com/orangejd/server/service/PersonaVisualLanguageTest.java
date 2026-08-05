package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonaVisualLanguageTest {

    @Test
    void eachProfileDimensionHasExactlyOneStableEquipmentSlot() {
        assertEquals(PersonaDimension.values().length, PersonaVisualLanguage.CATALOG.size());
        assertEquals(PersonaDimension.GOAL, PersonaVisualLanguage.requireSlot("goal_crown").dimension());
        assertEquals(PersonaDimension.SKILL, PersonaVisualLanguage.requireSlot("SKILL_VISOR").dimension());
        assertEquals(PersonaDimension.LEFT_HAND,
                PersonaVisualLanguage.requireSlot("LEFT_HAND_WEAPON").dimension());
        assertEquals(PersonaDimension.RIGHT_HAND,
                PersonaVisualLanguage.requireSlot("RIGHT_HAND_WEAPON").dimension());
    }

    @Test
    void rejectsCrossSlotItemsAndUnknownVariants() {
        assertThrows(BaseException.class,
                () -> PersonaVisualLanguage.validate("GOAL_CROWN", "CRAFT_VISOR", "CLASSIC"));
        assertThrows(BaseException.class,
                () -> PersonaVisualLanguage.validate("GOAL_CROWN", "COMPASS_CROWN", "UNKNOWN"));
    }
}
