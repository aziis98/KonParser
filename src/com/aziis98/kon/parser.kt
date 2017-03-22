package com.aziis98.kon

import com.aziis98.core.compiler.*
import java.util.*

/**
 * Created by aziis98 on 19/03/2017.
 */

sealed class NumberUnit {
    object Pure : NumberUnit()
    data class Named(val name: String) : NumberUnit()
}

sealed class KonValue {
    object None : KonValue()
}

data class KonString(val value: String) : KonValue() {
    override fun toString() = "\"$value\""
}
data class KonSymbol(val name: String) : KonValue() {
    override fun toString() = name
}

sealed class KonNumber<out N : Number>(val value: N, val numberUnit: NumberUnit) : KonValue() {
    override fun hashCode() = value.hashCode() + 31 * numberUnit.hashCode()

    override fun equals(other: Any?) = (other as? KonNumber<*>)?.let { it.value == value && it.numberUnit == numberUnit } ?: false

    override fun toString() = "$value ${(numberUnit as? NumberUnit.Named)?.name ?: ""}"
}

class KonDecimal(value: Double, numberUnit: NumberUnit) : KonNumber<Double>(value, numberUnit)
class KonInteger(value: Int, numberUnit: NumberUnit) : KonNumber<Int>(value, numberUnit)

data class KonElement(val name: String,
                      val attributes: Map<String, KonValue>,
                      val children: List<KonElement>,
                      val value: KonValue) {
    override fun toString(): String {
        val _attr = if (attributes.isEmpty()) "" else attributes.entries.map { "${it.key} = ${it.value}" }.joinToString(", ", "(", ")")
        val _children = if(children.isEmpty()) "" else children.map { "\t$it" }.joinToString("\n", " {\n", "\n}")
        val _value = if (value == KonValue.None) "" else " = $value"

        return "$name$_attr$_children$_value"
    }
}

internal class WithinCondenser(val startToken: String, val endToken: String) : ListStartEndCondenser<String>() {

    companion object {
        val stringCondenser = WithinCondenser("\"", "\"")
    }

    override fun matchStart(value: String) = value == startToken

    override fun matchEnd(value: String) = value == endToken

    override fun merge(list: List<String>) = list.joinToString("")
}

object KonFormat {

    private val tokenizer = Tokenizer.create {
        rule(Char::isLetter, Char::isDigit)
        rule({ it == '-' || it == '+' }, Char::isDigit)

        sameRule(Char::isLetter)
        sameRule(Char::isDigit)
        sameRule({ it.isWhitespace() && it != '\n' })

        simmetricRule(Char::isDigit, { it == '.' })
        simmetricRule(Char::isLetter, { it == '_' })
        simmetricRule(Char::isLetter, { it == '-' })
    }

    fun parse(source: String): List<KonElement> {
        val tokens = tokenizer
                .tokenize(source)
                .let { WithinCondenser.stringCondenser.group(it) }
                .filter { it.isNotBlank() || it == "\n" }
                .toCollection(LinkedList())


        fun parseValue(): KonValue {
            val token = tokens.pop()

            return when {
                token.startsWith("\"") && token.endsWith("\"") -> {
                    KonString(token.substring(1, token.length - 1))
                }
                token.matches(Regex("[+-]?\\d+\\.\\d+")) -> {
                    val unit = tokens.peek().takeIf { it.matches(Regex("\\w+")) }?.let {
                        tokens.pop()
                        NumberUnit.Named(it)
                    } ?: NumberUnit.Pure

                    KonDecimal(token.toDouble(), unit)
                }
                token.matches(Regex("[+-]?\\d+")) -> {
                    val unit = tokens.peek().takeIf { it.matches(Regex("\\w+")) }?.let {
                        tokens.pop()
                        NumberUnit.Named(it)
                    } ?: NumberUnit.Pure

                    KonInteger(token.toInt(), unit)
                }
                token.matches(Regex("\\w+")) -> {
                    KonSymbol(token)
                }

                else -> error("Illegal value '$token'")
            }
        }

        fun parseElement(): KonElement {
            val name = tokens.pop()
            val attributes = mutableMapOf<String, KonValue>()

            tokens.popAllNewlines()

            if (tokens.peek() == "(") {
                tokens.pop()
                while (tokens.peek() != ")") {
                    val attributeName = tokens.pop()

                    testExpected(tokens.pop(), "=")

                    val attributeValue = parseValue()

                    attributes.put(attributeName, attributeValue)

                    if (tokens.peek() == ",") tokens.pop()
                }
                tokens.pop()
            }

            val children = mutableListOf<KonElement>()

            if (tokens.peek() == "{") {
                tokens.pop()
                while (tokens.peek() != "}") {
                    tokens.popAllNewlines()
                    children += parseElement()
                    tokens.popAllNewlines()
                }
                tokens.pop()
            }

            val value = if (tokens.peek() == "=") {
                tokens.pop()
                parseValue()
            }
            else {
                KonValue.None
            }

            return KonElement(name, attributes, children, value)
        }

        val rootElements = mutableListOf<KonElement>()

        while (tokens.isNotEmpty()) {
            tokens.popAllNewlines()
            rootElements += parseElement()
            tokens.popAllNewlines()
        }

        return rootElements
    }

}

private fun <T> testExpected(value: T, expected: T) {
    assert(value == expected) { "Expected '$expected' instead got '$value'" }
}

private fun LinkedList<String>.popAllNewlines() {
    while (peek() == "\n") pop()
}