/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.base

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.psi.KtElement

/**
 * Represents a [compile-time constant](https://kotlinlang.org/docs/properties.html#compile-time-constants) value. It can be used as a
 * `const val` initializer or as an annotation argument.
 *
 * [KaConstantValue] can also represent evaluated values. For example, the constant expression `1 + 2` can be represented as an [IntValue]
 * with a value `3`.
 */
public sealed interface KaConstantValue {
    /**
     * The value of the constant. The type of [value] matches its represented class, e.g. [BooleanValue.value] will return a [Boolean].
     *
     * For [NullValue] and [ErrorValue], [value] contains a special value.
     */
    public val value: Any?

    /**
     * A source element from which the value was created. The PSI might be `null` for constants from non-source files.
     */
    public val sourcePsi: KtElement?

    /**
     * Renders the value as a representable constant value [String], such as `1`, `2f`, `3u` `null`, `"text"`.
     */
    public fun render(): String

    /** Represents a `null` value of some class type. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface NullValue : KaConstantValue

    /** Represents a [Boolean] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface BooleanValue : KaConstantValue {
        override val value: Boolean
    }

    /** Represents a [Char] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface CharValue : KaConstantValue {
        override val value: Char
    }

    /** Represents a [Byte] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface ByteValue : KaConstantValue {
        override val value: Byte
    }

    /** Represents a [UByte] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface UByteValue : KaConstantValue {
        override val value: UByte
    }

    /** Represents a [Short] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface ShortValue : KaConstantValue {
        override val value: Short
    }

    /** Represents a [UShort] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface UShortValue : KaConstantValue {
        override val value: UShort
    }

    /** Represents an [Int] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface IntValue : KaConstantValue {
        override val value: Int
    }

    /** Represents a [UInt] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface UIntValue : KaConstantValue {
        override val value: UInt
    }

    /** Represents a [Long] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface LongValue : KaConstantValue {
        override val value: Long
    }

    /** Represents a [ULong] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface ULongValue : KaConstantValue {
        override val value: ULong
    }

    /** Represents a [Float] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface FloatValue : KaConstantValue {
        override val value: Float
    }

    /** Represents a [Double] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface DoubleValue : KaConstantValue {
        override val value: Double
    }

    /** Represents a [String] value. */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface StringValue : KaConstantValue {
        override val value: String
    }

    /** Represents either a non-constant value, or a constant evaluation error (such as a division by zero). */
    @SubclassOptInRequired(KaImplementationDetail::class)
    public interface ErrorValue : KaConstantValue {
        public val errorMessage: String
        override val value: Nothing
    }
}
