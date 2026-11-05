package com.example.skybackend.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeywordIntentMatcherTest {

    private final KeywordIntentMatcher matcher = new KeywordIntentMatcher();

    @Test
    void extractsAFlightIntentCandidateFromNaturalLanguage() {
        assertEquals("return_home", matcher.suggest("请帮我返航回到起飞点"));
        assertEquals("fly_to", matcher.suggest("慢一点往前飞五米"));
    }

    @Test
    void doesNotChooseOneActionWhenTheUserSaysMultipleActions() {
        assertNull(matcher.suggest("先拍照再返航"));
    }

    @Test
    void doesNotInventAnIntentForUnrelatedText() {
        assertNull(matcher.suggest("今天天气不错"));
    }
}
