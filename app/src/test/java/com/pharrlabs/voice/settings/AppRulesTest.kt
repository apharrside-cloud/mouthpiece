package com.pharrlabs.voice.settings

import com.pharrlabs.voice.clean.PeriodMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRulesTest {
    private class Mem : RulesStorage {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    @Test fun unknownAppHasDefaultRule() {
        assertEquals(AppRule(), AppRules(Mem()).ruleFor("com.example"))
    }

    @Test fun savedRuleRoundTrips() {
        val mem = Mem()
        AppRules(mem).set("com.slack", AppRule(period = PeriodMode.Never))
        assertEquals(PeriodMode.Never, AppRules(mem).ruleFor("com.slack").period)
    }

    @Test fun settingDefaultRuleRemovesEntry() {
        val mem = Mem()
        val rules = AppRules(mem)
        rules.set("com.slack", AppRule(period = PeriodMode.Never))
        rules.set("com.slack", AppRule())
        assertNull(rules.all()["com.slack"])
    }

    @Test fun corruptJsonFallsBackToDefaults() {
        val mem = Mem().apply { json = "{not json" }
        assertEquals(AppRule(), AppRules(mem).ruleFor("anything"))
    }

    @Test fun unknownEnumValueFallsBackToAuto() {
        val mem = Mem().apply { json = """{"com.x":{"period":"Bogus"}}""" }
        assertEquals(PeriodMode.Auto, AppRules(mem).ruleFor("com.x").period)
    }

    @Test fun styleRoundTrips() {
        val mem = Mem()
        AppRules(mem).set("com.slack", AppRule(style = com.pharrlabs.voice.clean.Style.Formal))
        assertEquals(com.pharrlabs.voice.clean.Style.Formal, AppRules(mem).ruleFor("com.slack").style)
    }

    @Test fun unsetStyleIsNull() {
        val mem = Mem()
        AppRules(mem).set("com.slack", AppRule(period = PeriodMode.Never))
        assertNull(AppRules(mem).ruleFor("com.slack").style)
    }
}
