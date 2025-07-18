/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa.util

import kotlinx.collections.immutable.PersistentMap
import org.jetbrains.kotlin.contracts.description.MarkedEventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeVisited
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode

data class EventOccurrencesRangeAtNode(
    val range: MarkedEventOccurrencesRange<CFGNode<*>>,
    /**
     * Signals that the property, though initialized, isn't initialized properly (e.g., read before written).
     * The absence of `lateinit` results in an initialization error.
     *
     * Was introduced to prevent false positive `UNNECESSARY_LATEINIT`.
     */
    val mustBeLateinit: Boolean,
)

typealias EventOccurrencesRangeInfo<K> = PersistentMap<K, EventOccurrencesRangeAtNode>

typealias PathAwareEventOccurrencesRangeInfo<K> = PathAwareControlFlowInfo<K, EventOccurrencesRangeAtNode>

abstract class EventCollectingControlFlowGraphVisitor<K : Any> : PathAwareControlFlowGraphVisitor<K, EventOccurrencesRangeAtNode>() {
    override fun mergeInfo(
        a: EventOccurrencesRangeInfo<K>,
        b: EventOccurrencesRangeInfo<K>,
        node: CFGNode<*>
    ): EventOccurrencesRangeInfo<K> {
        val isUnion = node.isUnion
        if (isUnion && a.isEmpty()) return b
        // For union nodes, iterating over keys not present in the other branch is pointless as the result
        // is unchanged. For non-union nodes, lower bounds for keys only present on one side become 0.
        return (if (isUnion) b.keys else a.keys union b.keys).associateWithTo(a.builder()) { symbol ->
            val kind1 = a[symbol]?.range ?: MarkedEventOccurrencesRange.Zero
            val kind2 = b[symbol]?.range ?: MarkedEventOccurrencesRange.Zero
            val newKind = if (kind1.location != null && kind1 == kind2) {
                // If ranges are equal and have the same location, the event happened before branching:
                //   <x>; if (p) { ... } else { ... }
                //   ExactlyOnce(x) ---> ExactlyOnce(x) ---> ExactlyOnce(x)
                //                   \-> ExactlyOnce(x) -/
                kind1
            } else if (isUnion) {
                when {
                    kind1 == MarkedEventOccurrencesRange.Zero -> kind2
                    kind2 == MarkedEventOccurrencesRange.Zero -> kind1
                    // Otherwise the event happens more than once (in different locations):
                    //   callBothFunctions({ <x> }, { <y> })
                    //   Zero ---> ExactlyOnce(x) ---> MoreThanOnce
                    //         \-> ExactlyOnce(y) -/
                    // Sum of two non-zero ranges cannot be `ExactlyOnce` or `AtMostOnce`. It should also not be possible
                    // to get a union of `ExactlyOnce` and `AtMostOnce` for the same location (in which case the correct
                    // result would be `ExactlyOnce`...probably...if it made any sense in the first place).
                    else -> (kind1.withoutMarker + kind2.withoutMarker).at(null)
                }
            } else {
                val newLocation = when {
                    kind1 == MarkedEventOccurrencesRange.Zero -> kind2.location
                    kind2 == MarkedEventOccurrencesRange.Zero -> kind1.location
                    // For a non-union node, there can be cases where we merge different kinds for the same location:
                    //   try { A; <x>; B } catch (e: Exception) { }
                    //   Zero ---> A ---> ExactlyOnce(x) ---> B -----[success]----------> AtMostOnce(x)
                    //          \--------\-----------------\-[catch]-> AtMostOnce(x) -/
                    // But most of the time the locations are different, in which case `node.fir` is a FIR parent of both:
                    //   if (p) { <x> } else { <y> }
                    //   Zero ---> ExactlyOnce(x) ---> ExactlyOnce(node [references FIR for the entire `if`])
                    //         \-> ExactlyOnce(y) -/
                    else -> kind1.location.takeIf { it == kind2.location } ?: node
                }
                (kind1.withoutMarker or kind2.withoutMarker).at(newLocation)
            }
            val aMustBeLateinit = a[symbol]?.mustBeLateinit == true
            val bMustBeLateinit = b[symbol]?.mustBeLateinit == true
            EventOccurrencesRangeAtNode(newKind, mustBeLateinit = aMustBeLateinit || bMustBeLateinit)
        }.build()
    }
}

fun <K : Any> PathAwareEventOccurrencesRangeInfo<K>.addRange(
    key: K,
    range: EventOccurrencesRangeAtNode,
): PathAwareEventOccurrencesRangeInfo<K> =
    if (!range.range.canBeVisited() && !range.mustBeLateinit) this else transformValues {
        val oldRange = it[key] ?: return@transformValues it.put(key, range)
        val combinedRange = when {
            !range.range.canBeVisited() -> oldRange.range
            // Can discard the old location since the sum can only be `ExactlyOnce` or `AtMostOnce`
            // if the old range is `Zero`.
            else -> (oldRange.range.withoutMarker + range.range.withoutMarker).at(range.range.location)
        }
        val combinedMustBeLateinit = oldRange.mustBeLateinit || range.mustBeLateinit
        it.put(key, EventOccurrencesRangeAtNode(combinedRange, mustBeLateinit = combinedMustBeLateinit))
    }

fun <K : Any> PathAwareEventOccurrencesRangeInfo<K>.addRangeIfEmpty(
    key: K,
    range: EventOccurrencesRangeAtNode,
): PathAwareEventOccurrencesRangeInfo<K> =
    if (!range.range.canBeVisited() && !range.mustBeLateinit) this else transformValues {
        when {
            key in it -> it
            else -> it.put(key, range)
        }
    }

fun <K : Any> PathAwareEventOccurrencesRangeInfo<K>.overwriteRange(
    key: K,
    range: EventOccurrencesRangeAtNode,
): PathAwareEventOccurrencesRangeInfo<K> =
    transformValues { it.put(key, range) }

fun <K : Any> PathAwareEventOccurrencesRangeInfo<K>.removeRange(key: K): PathAwareEventOccurrencesRangeInfo<K> =
    transformValues { it.remove(key) }
