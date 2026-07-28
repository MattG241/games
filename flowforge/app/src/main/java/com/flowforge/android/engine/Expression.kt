package com.flowforge.android.engine

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Everything a mapping can see while a scenario runs.
 * `bundles` is keyed by module id, so `{{3.body}}` reads module 3's output.
 */
class EvalScope(
    val bundles: MutableMap<Int, Any?> = linkedMapOf(),
    val vars: MutableMap<String, Any?> = linkedMapOf(),
) {
    fun copyForBranch(): EvalScope = EvalScope(LinkedHashMap(bundles), vars)

    fun resolveRoot(name: String): Any? {
        name.toIntOrNull()?.let { return bundles[it] }
        return when (name) {
            "vars" -> vars
            "trigger" -> bundles.entries.minByOrNull { it.key }?.value
            "now" -> System.currentTimeMillis().toDouble()
            "timestamp" -> System.currentTimeMillis().toDouble()
            "uuid" -> UUID.randomUUID().toString()
            else -> if (vars.containsKey(name)) vars[name] else null
        }
    }
}

/**
 * Resolves Make-style `{{ ... }}` mappings. Supports paths (`2.json.items[0].name`),
 * arithmetic, and a library of functions using `;` between arguments.
 */
object Expression {

    fun render(template: String?, scope: EvalScope): String {
        if (template.isNullOrEmpty()) return ""
        if (!template.contains("{{")) return template
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            val open = template.indexOf("{{", i)
            if (open < 0) {
                out.append(template, i, template.length); break
            }
            out.append(template, i, open)
            val close = findClose(template, open + 2)
            if (close < 0) {
                out.append(template, open, template.length); break
            }
            val inner = template.substring(open + 2, close)
            out.append(Values.asText(evalInner(inner, scope)))
            i = close + 2
        }
        return out.toString()
    }

    /** Same as [render] but keeps the native type when the field is exactly one mapping. */
    fun evaluate(template: String?, scope: EvalScope): Any? {
        val t = template?.trim().orEmpty()
        if (t.startsWith("{{") && t.endsWith("}}")) {
            val close = findClose(t, 2)
            if (close == t.length - 2) return evalInner(t.substring(2, close), scope)
        }
        return render(template, scope)
    }

    private fun findClose(s: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < s.length - 1) {
            if (s[i] == '{' && s[i + 1] == '{') { depth++; i += 2; continue }
            if (s[i] == '}' && s[i + 1] == '}') {
                if (depth == 0) return i
                depth--; i += 2; continue
            }
            i++
        }
        return -1
    }

    private fun evalInner(src: String, scope: EvalScope): Any? = try {
        val parser = Parser(Lexer(src).lex())
        val node = parser.parseExpression()
        eval(node, scope)
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ AST

    private sealed interface Node
    private data class Lit(val value: Any?) : Node
    private data class Path(val root: String, val steps: List<Any>) : Node // String key or Int index
    private data class Call(val name: String, val args: List<Node>) : Node
    private data class Bin(val op: String, val left: Node, val right: Node) : Node

    // ------------------------------------------------------------------ lexer

    private data class Token(val kind: String, val text: String)

    private class Lexer(private val src: String) {
        fun lex(): List<Token> {
            val out = mutableListOf<Token>()
            var i = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    c.isWhitespace() -> i++
                    c == '"' || c == '\'' -> {
                        val quote = c
                        val sb = StringBuilder()
                        i++
                        while (i < src.length && src[i] != quote) {
                            if (src[i] == '\\' && i + 1 < src.length) { sb.append(src[i + 1]); i += 2 }
                            else { sb.append(src[i]); i++ }
                        }
                        i++
                        out += Token("str", sb.toString())
                    }
                    c.isDigit() -> {
                        val start = i
                        while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                        // `1.body` is a module path, not a decimal — back off the dot.
                        var text = src.substring(start, i)
                        if (text.endsWith(".")) { text = text.dropLast(1); i-- }
                        val dot = text.indexOf('.')
                        if (dot >= 0 && dot + 1 < text.length && !text[dot + 1].isDigit()) {
                            i = start + dot
                            text = text.substring(0, dot)
                        }
                        out += Token("num", text)
                    }
                    c.isLetter() || c == '_' -> {
                        val start = i
                        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                        out += Token("ident", src.substring(start, i))
                    }
                    c == '<' || c == '>' || c == '=' || c == '!' -> {
                        val twoChar = i + 1 < src.length && src[i + 1] == '='
                        val text = if (twoChar) src.substring(i, i + 2) else c.toString()
                        out += Token(text, text)
                        i += if (twoChar) 2 else 1
                    }
                    else -> { out += Token(c.toString(), c.toString()); i++ }
                }
            }
            out += Token("eof", "")
            return out
        }
    }

    // ------------------------------------------------------------------ parser

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private fun peek() = tokens[pos]
        private fun next() = tokens[pos++]
        private fun accept(kind: String): Boolean {
            if (peek().kind == kind) { pos++; return true }
            return false
        }

        fun parseExpression(): Node = parseComparison()

        private fun parseComparison(): Node {
            var left = parseAdditive()
            while (peek().kind in COMPARISONS) {
                val op = next().kind
                left = Bin(op, left, parseAdditive())
            }
            return left
        }

        private fun parseAdditive(): Node {
            var left = parseMultiplicative()
            while (peek().kind == "+" || peek().kind == "-") {
                val op = next().kind
                left = Bin(op, left, parseMultiplicative())
            }
            return left
        }

        private fun parseMultiplicative(): Node {
            var left = parseUnary()
            while (peek().kind == "*" || peek().kind == "/" || peek().kind == "%") {
                val op = next().kind
                left = Bin(op, left, parseUnary())
            }
            return left
        }

        private fun parseUnary(): Node {
            if (peek().kind == "-") { next(); return Bin("-", Lit(0.0), parseUnary()) }
            return parsePrimary()
        }

        private fun parsePrimary(): Node {
            val t = peek()
            return when (t.kind) {
                "(" -> { next(); val e = parseExpression(); accept(")"); e }
                "str" -> { next(); Lit(t.text) }
                "num" -> {
                    next()
                    if (peek().kind == "." || peek().kind == "[") parsePathFrom(t.text)
                    else Lit(t.text.toDoubleOrNull() ?: t.text)
                }
                "ident" -> {
                    next()
                    if (peek().kind == "(") {
                        next()
                        val args = mutableListOf<Node>()
                        if (peek().kind != ")") {
                            args += parseExpression()
                            while (peek().kind == ";" || peek().kind == ",") { next(); args += parseExpression() }
                        }
                        accept(")")
                        Call(t.text.lowercase(), args)
                    } else when (t.text) {
                        "true" -> Lit(true)
                        "false" -> Lit(false)
                        "null" -> Lit(null)
                        else -> parsePathFrom(t.text)
                    }
                }
                else -> { next(); Lit(null) }
            }
        }

        private fun parsePathFrom(root: String): Node {
            val steps = mutableListOf<Any>()
            while (true) {
                when (peek().kind) {
                    "." -> {
                        next()
                        val k = next()
                        steps += k.text
                    }
                    "[" -> {
                        next()
                        val k = next()
                        steps += k.text.toIntOrNull() ?: k.text
                        accept("]")
                    }
                    else -> return Path(root, steps)
                }
            }
        }
    }

    // ------------------------------------------------------------------ eval

    private fun eval(node: Node, scope: EvalScope): Any? = when (node) {
        is Lit -> node.value
        is Path -> {
            var cur = scope.resolveRoot(node.root)
            for (step in node.steps) {
                cur = when (val c = cur) {
                    is Map<*, *> -> c[step.toString()]
                    is List<*> -> when (step) {
                        is Int -> c.getOrNull(step)
                        else -> null
                    }
                    is String -> Values.parseJsonOrNull(c)?.let { Values.dig(it, step.toString()) }
                    else -> null
                }
            }
            cur
        }
        is Call -> callFunction(node.name, node.args.map { eval(it, scope) })
        is Bin -> {
            val l = eval(node.left, scope)
            val r = eval(node.right, scope)
            val ln = Values.asNumber(l)
            val rn = Values.asNumber(r)
            when (node.op) {
                "==" -> Values.asText(l).equals(Values.asText(r), ignoreCase = true)
                "!=" -> !Values.asText(l).equals(Values.asText(r), ignoreCase = true)
                "<", ">", "<=", ">=" -> {
                    val comparison =
                        if (ln != null && rn != null) ln.compareTo(rn)
                        else Values.asText(l).compareTo(Values.asText(r))
                    when (node.op) {
                        "<" -> comparison < 0
                        ">" -> comparison > 0
                        "<=" -> comparison <= 0
                        else -> comparison >= 0
                    }
                }
                "+" -> if (ln == null || rn == null) Values.asText(l) + Values.asText(r) else ln + rn
                "-" -> if (ln == null || rn == null) null else ln - rn
                "*" -> if (ln == null || rn == null) null else ln * rn
                "/" -> if (ln == null || rn == null || rn == 0.0) null else ln / rn
                "%" -> if (ln == null || rn == null || rn == 0.0) null else ln % rn
                else -> null
            }
        }
    }

    private val COMPARISONS = setOf("<", ">", "<=", ">=", "==", "!=")

    private fun arg(args: List<Any?>, i: Int): Any? = args.getOrNull(i)
    private fun str(args: List<Any?>, i: Int): String = Values.asText(args.getOrNull(i))
    private fun num(args: List<Any?>, i: Int, fallback: Double = 0.0): Double =
        Values.asNumber(args.getOrNull(i)) ?: fallback

    private fun callFunction(name: String, a: List<Any?>): Any? = when (name) {
        // text
        "upper" -> str(a, 0).uppercase()
        "lower" -> str(a, 0).lowercase()
        "trim" -> str(a, 0).trim()
        "length" -> when (val v = arg(a, 0)) {
            is Collection<*> -> v.size.toDouble()
            is Map<*, *> -> v.size.toDouble()
            else -> str(a, 0).length.toDouble()
        }
        "substring" -> {
            val s = str(a, 0)
            val from = num(a, 1).toInt().coerceIn(0, s.length)
            val to = if (a.size > 2) num(a, 2).toInt().coerceIn(from, s.length) else s.length
            s.substring(from, to)
        }
        "replace" -> str(a, 0).replace(str(a, 1), str(a, 2))
        "split" -> str(a, 0).split(str(a, 1).ifEmpty { "," })
        "join" -> Values.asList(arg(a, 0)).joinToString(if (a.size > 1) str(a, 1) else ", ") { Values.asText(it) }
        "contains" -> str(a, 0).contains(str(a, 1), ignoreCase = true)
        "startswith" -> str(a, 0).startsWith(str(a, 1), ignoreCase = true)
        "endswith" -> str(a, 0).endsWith(str(a, 1), ignoreCase = true)
        "indexof" -> str(a, 0).indexOf(str(a, 1)).toDouble()
        "padstart" -> str(a, 0).padStart(num(a, 1).toInt(), (str(a, 2).firstOrNull() ?: '0'))
        "capitalize" -> str(a, 0).replaceFirstChar { it.uppercase() }
        "stripHtml", "striphtml" -> str(a, 0).replace(Regex("<[^>]*>"), "")
        "match" -> Regex(str(a, 1)).find(str(a, 0))?.value
        "matchall" -> Regex(str(a, 1)).findAll(str(a, 0)).map { it.value }.toList()

        // arrays / objects
        "first" -> Values.asList(arg(a, 0)).firstOrNull()
        "last" -> Values.asList(arg(a, 0)).lastOrNull()
        "count" -> Values.asList(arg(a, 0)).size.toDouble()
        "get" -> Values.dig(arg(a, 0), str(a, 1))
        "pluck", "map" -> Values.asList(arg(a, 0)).map { Values.dig(it, str(a, 1)) }
        "sum" -> Values.asList(arg(a, 0)).sumOf { Values.asNumber(it) ?: 0.0 }
        "keys" -> (arg(a, 0) as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList<String>()
        "values" -> (arg(a, 0) as? Map<*, *>)?.values?.toList() ?: emptyList<Any?>()
        "sort" -> Values.asList(arg(a, 0)).sortedBy { Values.asText(it) }
        "reverse" -> Values.asList(arg(a, 0)).reversed()
        "distinct" -> Values.asList(arg(a, 0)).distinct()
        "slice" -> {
            val list = Values.asList(arg(a, 0))
            val from = num(a, 1).toInt().coerceIn(0, list.size)
            val to = if (a.size > 2) num(a, 2).toInt().coerceIn(from, list.size) else list.size
            list.subList(from, to)
        }

        // logic
        "if" -> if (Values.asBool(arg(a, 0))) arg(a, 1) else arg(a, 2)
        "ifempty", "default" -> arg(a, 0).takeIf { Values.asText(it).isNotBlank() } ?: arg(a, 1)
        "not" -> !Values.asBool(arg(a, 0))
        "and" -> a.all { Values.asBool(it) }
        "or" -> a.any { Values.asBool(it) }
        "equals", "eq" -> Values.asText(arg(a, 0)).equals(Values.asText(arg(a, 1)), ignoreCase = true)

        // numbers
        "number", "tonumber" -> Values.asNumber(arg(a, 0))
        "round" -> {
            val dp = num(a, 1).toInt()
            val f = Math.pow(10.0, dp.toDouble())
            (num(a, 0) * f).roundToLong() / f
        }
        "floor" -> floor(num(a, 0))
        "ceil" -> ceil(num(a, 0))
        "abs" -> abs(num(a, 0))
        "min" -> a.mapNotNull { Values.asNumber(it) }.minOrNull()
        "max" -> a.mapNotNull { Values.asNumber(it) }.maxOrNull()
        "random" -> {
            val lo = if (a.isEmpty()) 0.0 else num(a, 0)
            val hi = if (a.size > 1) num(a, 1) else 1.0
            lo + Math.random() * (hi - lo)
        }
        "randomint" -> {
            val lo = num(a, 0).toInt()
            val hi = num(a, 1, 100.0).toInt()
            (lo..maxOf(lo, hi)).random().toDouble()
        }

        // dates
        "now", "timestamp" -> System.currentTimeMillis().toDouble()
        "formatdate" -> {
            val ts = Values.asNumber(arg(a, 0)) ?: System.currentTimeMillis().toDouble()
            val pattern = if (a.size > 1) str(a, 1) else "yyyy-MM-dd HH:mm:ss"
            runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts.toLong())) }
                .getOrDefault("")
        }
        "parsedate" -> runCatching {
            SimpleDateFormat(str(a, 1), Locale.getDefault()).parse(str(a, 0))?.time?.toDouble()
        }.getOrNull()
        "addminutes" -> (Values.asNumber(arg(a, 0)) ?: 0.0) + num(a, 1) * 60_000.0
        "addhours" -> (Values.asNumber(arg(a, 0)) ?: 0.0) + num(a, 1) * 3_600_000.0
        "adddays" -> (Values.asNumber(arg(a, 0)) ?: 0.0) + num(a, 1) * 86_400_000.0

        // encoding
        "json", "stringify" -> Values.encode(arg(a, 0))
        "parsejson" -> Values.parseJsonOrNull(str(a, 0))
        "encodeurl" -> runCatching { URLEncoder.encode(str(a, 0), "UTF-8") }.getOrDefault("")
        "decodeurl" -> runCatching { URLDecoder.decode(str(a, 0), "UTF-8") }.getOrDefault("")
        "base64" -> runCatching { Base64.getEncoder().encodeToString(str(a, 0).toByteArray()) }.getOrDefault("")
        "unbase64" -> runCatching { String(Base64.getDecoder().decode(str(a, 0))) }.getOrDefault("")
        "uuid" -> UUID.randomUUID().toString()

        else -> null
    }

    /** Shared by the Filter module and per-route filters. */
    fun testCondition(left: Any?, op: String, right: Any?): Boolean {
        val l = Values.asText(left)
        val r = Values.asText(right)
        return when (op) {
            "equals" -> l.equals(r, ignoreCase = true)
            "not equals" -> !l.equals(r, ignoreCase = true)
            "contains" -> l.contains(r, ignoreCase = true)
            "not contains" -> !l.contains(r, ignoreCase = true)
            "starts with" -> l.startsWith(r, ignoreCase = true)
            "ends with" -> l.endsWith(r, ignoreCase = true)
            "matches regex" -> runCatching { Regex(r).containsMatchIn(l) }.getOrDefault(false)
            "greater than" -> (Values.asNumber(left) ?: 0.0) > (Values.asNumber(right) ?: 0.0)
            "less than" -> (Values.asNumber(left) ?: 0.0) < (Values.asNumber(right) ?: 0.0)
            "is empty" -> l.isBlank()
            "is not empty" -> l.isNotBlank()
            "is true" -> Values.asBool(left)
            else -> true
        }
    }
}
