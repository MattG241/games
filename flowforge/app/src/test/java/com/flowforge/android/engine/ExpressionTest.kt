package com.flowforge.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {

    private fun scope(): EvalScope = EvalScope(
        bundles = linkedMapOf(
            1 to mapOf("from" to "+61400111222", "text" to "  Deploy now  ", "level" to 12.0),
            2 to mapOf(
                "status" to 200.0,
                "json" to mapOf(
                    "items" to listOf(
                        mapOf("name" to "alpha", "qty" to 3.0),
                        mapOf("name" to "beta", "qty" to 7.0),
                    ),
                    "ok" to true,
                ),
            ),
        ),
        vars = linkedMapOf("site" to "sydney"),
    )

    @Test
    fun `resolves a simple module field`() {
        assertEquals("+61400111222", Expression.render("{{1.from}}", scope()))
    }

    @Test
    fun `interpolates inside surrounding text`() {
        assertEquals(
            "Status was 200 at sydney",
            Expression.render("Status was {{2.status}} at {{vars.site}}", scope()),
        )
    }

    @Test
    fun `walks nested objects and array indexes`() {
        assertEquals("beta", Expression.render("{{2.json.items[1].name}}", scope()))
    }

    @Test
    fun `applies text functions`() {
        assertEquals("DEPLOY NOW", Expression.render("{{upper(trim(1.text))}}", scope()))
    }

    @Test
    fun `does arithmetic on mapped numbers`() {
        assertEquals("19", Expression.render("{{1.level + 7}}", scope()))
        assertEquals("100", Expression.render("{{2.status / 2}}", scope()))
    }

    @Test
    fun `concatenates when either side is text`() {
        assertEquals("level:12", Expression.render("{{\"level:\" + 1.level}}", scope()))
    }

    @Test
    fun `evaluate keeps native types for a lone mapping`() {
        val list = Expression.evaluate("{{2.json.items}}", scope())
        assertTrue(list is List<*>)
        assertEquals(2, (list as List<*>).size)
    }

    @Test
    fun `if and comparisons drive branching values`() {
        assertEquals("low", Expression.render("{{if(1.level < 20; \"low\"; \"ok\")}}", scope()))
        assertEquals("ok", Expression.render("{{if(2.status < 20; \"low\"; \"ok\")}}", scope()))
        assertEquals("yes", Expression.render("{{if(2.status == 200; \"yes\"; \"no\")}}", scope()))
        assertEquals("yes", Expression.render("{{if(equals(2.status; 200); \"yes\"; \"no\")}}", scope()))
        assertEquals("true", Expression.render("{{1.level >= 12}}", scope()))
    }

    @Test
    fun `maps and sums over an array`() {
        assertEquals("alpha, beta", Expression.render("{{join(pluck(2.json.items; \"name\"); \", \")}}", scope()))
        assertEquals("10", Expression.render("{{sum(pluck(2.json.items; \"qty\"))}}", scope()))
    }

    @Test
    fun `unknown fields render as empty rather than throwing`() {
        assertEquals("", Expression.render("{{9.nope}}", scope()))
        assertEquals("a-b", Expression.render("a{{4.missing}}-b", scope()))
    }

    @Test
    fun `malformed expressions do not crash the run`() {
        assertEquals("", Expression.render("{{ upper( }}", scope()))
        assertEquals("{{unclosed", Expression.render("{{unclosed", scope()))
    }

    @Test
    fun `filter conditions cover the operator list`() {
        assertTrue(Expression.testCondition("hello world", "contains", "WORLD"))
        assertFalse(Expression.testCondition("hello", "equals", "goodbye"))
        assertTrue(Expression.testCondition(12.0, "less than", 20.0))
        assertTrue(Expression.testCondition("", "is empty", ""))
        assertTrue(Expression.testCondition("ab123", "matches regex", "\\d+"))
        assertFalse(Expression.testCondition("ab", "matches regex", "\\d+"))
    }

    @Test
    fun `values dig walks bracket and dot paths`() {
        val root = mapOf("a" to mapOf("b" to listOf(mapOf("c" to "found"))))
        assertEquals("found", Values.dig(root, "a.b[0].c"))
        assertEquals(null, Values.dig(root, "a.b[4].c"))
    }

    @Test
    fun `numbers render without a trailing decimal`() {
        assertEquals("42", Values.asText(42.0))
        assertEquals("4.5", Values.asText(4.5))
    }
}
