/*
 * Copyright 2023-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Copyright (C) 2020-2023 Brian Norman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.jetbrains.kotlin.powerassert.diagram

import org.jetbrains.kotlin.backend.common.implicitInvoke
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.SourceRangeInfo
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.powerassert.*

fun IrBuilderWithScope.irDiagramString(
    sourceFile: SourceFile,
    prefix: IrExpression? = null,
    call: IrCall,
    variables: List<IrTemporaryVariable>,
): IrExpression {
    val callInfo = sourceFile.getCompleteSourceRangeInfo(call)

    // Get call source string starting at the very beginning of the first line.
    // This is so multiline calls all start from the same column offset.
    val rows = sourceFile.getText(callInfo.startOffset - callInfo.startColumnNumber, callInfo.endOffset)
        .clearSourcePrefix(callInfo.startColumnNumber)
        .split("\n")

    val minSourceIndent = rows.minOf { line ->
        // Find index of first non-whitespace character.
        val indent = line.indexOfFirst { !it.isWhitespace() }
        if (indent == -1) Int.MAX_VALUE else indent
    }

    val valuesByRow = variables
        .filterIsInstance<IrTemporaryVariable.Displayable>()
        .map { it.toValueDisplay(callInfo) }
        .sortedBy { it.indent }
        .groupBy { it.row }

    return irConcat().apply {
        if (prefix != null) addArgument(prefix)

        for ((row, rowSource) in rows.withIndex()) {
            addArgument(
                irString {
                    appendLine()
                    val sourceLine = rowSource.substring(minOf(minSourceIndent, rowSource.length))
                    if (sourceLine.isNotBlank() && valuesByRow[row - 1] != null) appendLine() // Add an extra line after displayed values.
                    append(sourceLine)
                },
            )

            val rowValues = valuesByRow[row] ?: continue

            val lineTemplate = buildString {
                val indentations = rowValues.mapTo(hashSetOf()) { it.indent }
                val lastIndent = rowValues.last().indent
                for ((i, c) in rowSource.withIndex()) {
                    when {
                        i in indentations -> {
                            // Add bar at indents for value display.
                            append('|')
                            if (i == lastIndent) break // Do not add trailing whitespace.
                        }
                        c == '\t' -> append('\t') // Preserve tabs in source code.
                        else -> append(' ')
                    }
                }
            }

            addArgument(
                irString {
                    appendLine()
                    append(lineTemplate.substring(minSourceIndent))
                },
            )

            for (tmp in rowValues.asReversed()) {
                addArgument(
                    irString {
                        appendLine()
                        append(lineTemplate.substring(minSourceIndent, tmp.indent))
                    },
                )
                addArgument(irGet(tmp.value))
            }
        }

        addArgument(
            irString {
                appendLine()
            }
        )
    }
}

private fun String.clearSourcePrefix(offset: Int): String = buildString {
    for ((i, c) in this@clearSourcePrefix.withIndex()) {
        when {
            i >= offset -> {
                // Append the remaining characters and exit.
                append(this@clearSourcePrefix.substring(i))
                break
            }
            c == '\t' -> append('\t') // Preserve tabs.
            else -> append(' ') // Replace all other characters with spaces.
        }
    }
}

private data class ValueDisplay(
    val value: IrVariable,
    val indent: Int,
    val row: Int,
)

private fun IrTemporaryVariable.Displayable.toValueDisplay(
    originalInfo: SourceRangeInfo,
): ValueDisplay {
    var indent = sourceRangeInfo.startColumnNumber
    var row = sourceRangeInfo.startLineNumber - originalInfo.startLineNumber

    val source = text
    val columnOffset = findDisplayOffset(original, sourceRangeInfo, source)

    val prefix = source.substring(0, columnOffset)
    val rowShift = prefix.count { it == '\n' }
    if (rowShift == 0) {
        indent += columnOffset
    } else {
        row += rowShift
        indent = columnOffset - (prefix.lastIndexOf('\n') + 1)
    }

    return ValueDisplay(temporary, indent, row)
}

/**
 * Responsible for determining the diagram display offset of the expression
 * beginning from the startOffset of the expression.
 *
 * Equality:
 * ```
 * number == 42
 * | <- startOffset
 *        | <- display offset: 7
 * ```
 *
 * Arithmetic:
 * ```
 * i + 2
 * | <- startOffset
 *   | <- display offset: 2
 * ```
 *
 * Infix:
 * ```
 * 1 shl 2
 * | <- startOffset
 *   | <- display offset: 2
 * ```
 *
 * Standard:
 * ```
 * 1.shl(2)
 *   | <- startOffset
 *   | <- display offset: 0
 * ```
 */
internal fun findDisplayOffset(
    expression: IrExpression,
    sourceRangeInfo: SourceRangeInfo,
    source: String,
): Int {
    return when (expression) {
        is IrMemberAccessExpression<*> -> memberAccessOffset(expression, sourceRangeInfo, source)
        is IrTypeOperatorCall -> typeOperatorOffset(expression, sourceRangeInfo, source)
        else -> 0
    }
}

private fun memberAccessOffset(
    expression: IrMemberAccessExpression<*>,
    sourceRangeInfo: SourceRangeInfo,
    source: String,
): Int {
    if (expression !is IrCall) return 0
    val owner = expression.symbol.owner
    if (owner.isInfix || owner.isOperator || owner.origin == IrBuiltIns.BUILTIN_OPERATOR) {
        if (expression.implicitInvoke) {
            val receiver = expression.getExplicitReceiver() ?: return 0
            var offset = receiver.endOffset - sourceRangeInfo.startOffset
            while (offset in source.indices && source[offset] == ')') offset++
            return offset
        }

        val lhs = expression.binaryOperatorLhs() ?: return 0
        return when (expression.origin) {
            IrStatementOrigin.GET_ARRAY_ELEMENT -> lhs.endOffset - sourceRangeInfo.startOffset
            else -> binaryOperatorOffset(lhs, sourceRangeInfo, source)
        }
    }

    return 0
}

private fun typeOperatorOffset(
    expression: IrTypeOperatorCall,
    sourceRangeInfo: SourceRangeInfo,
    source: String,
): Int {
    return when (expression.operator) {
        IrTypeOperator.INSTANCEOF,
        IrTypeOperator.NOT_INSTANCEOF,
        IrTypeOperator.SAFE_CAST,
            -> binaryOperatorOffset(expression.argument, sourceRangeInfo, source)

        else -> 0
    }
}

/**
 * The offset of the infix operator/function token itself.
 *
 * @param lhs The left-hand side expression of the operator.
 * @param wholeOperatorSourceRangeInfo The source range of the whole operator expression.
 * @param wholeOperatorSource The source text of the whole operator expression.
 */
private fun binaryOperatorOffset(lhs: IrExpression, wholeOperatorSourceRangeInfo: SourceRangeInfo, wholeOperatorSource: String): Int {
    val offset = lhs.endOffset - wholeOperatorSourceRangeInfo.startOffset
    if (offset < 0 || offset >= wholeOperatorSource.length) return 0 // infix function called using non-infix syntax

    KotlinLexer().run {
        start(wholeOperatorSource, offset, wholeOperatorSource.length)
        while (tokenType != null && tokenType != KtTokens.EOF && (tokenType == KtTokens.DOT || tokenType !in KtTokens.OPERATIONS)) {
            advance()
        }
        if (tokenStart >= wholeOperatorSource.length) return 0
        return tokenStart
    }
}

/**
 * The left-hand side expression of an infix operator/function that takes into account special cases like `in`, `!in` and `!=` operators
 * that have a more complex structure than just a single call with two arguments.
 */
private fun IrCall.binaryOperatorLhs(): IrExpression? = when (origin) {
    IrStatementOrigin.EXCLEQ, IrStatementOrigin.EXCLEQEQ -> {
        // The `!=` operator call is actually a sugar for `lhs.equals(rhs).not()`.
        // The `!==` operator call is actually a sugar for `(lhs === rhs).not()`.
        val innerCall = (arguments[0] as? IrCall)?.takeIf { it.isInnerOfNotEqualOperator() } ?: this
        innerCall.simpleBinaryOperatorLhs()
    }
    IrStatementOrigin.IN, IrStatementOrigin.NOT_IN -> {
        val innerCall = when (origin) {
            // The `!in` operator call is actually sugar for `rhs.contains(lhs).not()`.
            IrStatementOrigin.NOT_IN -> arguments[0] as? IrCall ?: this
            // The `in` operator call is actually sugar for `rhs.contains(lhs)`.
            else -> this
        }

        // There are operator functions that do not conform to the normal signature requirement:
        // * `operator fun CharSequence.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean`
        // * `operator fun CharSequence.contains(char: Char, ignoreCase: Boolean = false)`
        // So, need to extract the argument for the first regular parameter.
        val function = innerCall.symbol.owner
        val parameter = function.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return null
        innerCall.arguments[parameter]
    }
    IrStatementOrigin.LT, IrStatementOrigin.GT, IrStatementOrigin.LTEQ, IrStatementOrigin.GTEQ -> {
        // Comparison operator calls are actually sugar for `lhs.compareTo(rhs) <> 0`.
        val innerCall = (arguments[0] as? IrCall)?.takeIf { it.isInnerOfComparisonOperator() } ?: this
        innerCall.simpleBinaryOperatorLhs()
    }
    else -> simpleBinaryOperatorLhs()
}

/**
 * The left-hand side expression of an infix operator/function:
 * * For single-value operators returns `null`,
 * * For all others, returns the explicit receiver or the first regular argument.
 */
private fun IrCall.simpleBinaryOperatorLhs(): IrExpression? {
    val parameters = symbol.owner.parameters
    val singleReceiver =
        1 == parameters.count { it.kind == IrParameterKind.DispatchReceiver || it.kind == IrParameterKind.ExtensionReceiver }
    if (singleReceiver && parameters.none { it.kind == IrParameterKind.Regular }) {
        return null
    }

    getExplicitReceiver()?.let { return it }
    if (symbol.owner.origin != IrBuiltIns.BUILTIN_OPERATOR) return null
    return parameters.firstOrNull { it.kind == IrParameterKind.Regular }?.let { arguments[it] }
}
