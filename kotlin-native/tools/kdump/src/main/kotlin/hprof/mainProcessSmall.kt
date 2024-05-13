package hprof

import java.io.*
import base.*
import kotlin.random.*

fun main() {
  File("small.hprof")
    .readProfile()
    .toSingleHeapDump()
    .run { process(toIndex()) }
    .let { profile ->
      File("small.processed.hprof").write(profile)
      File("small.processed.hprof").readProfile().dump()
    }
}

fun Profile.process(index: Index): Profile =
  copy(records =
    records
      .map { it.process(index) }
      .filterNotNull()
      .map { record ->
        when (record) {
          is HeapDump ->
            HeapDump(
              record
                .records
                .map { it.process(index) }
                .filterNotNull()
                .plus(List(11) { index ->
                  PrimitiveArrayDump(
                    arrayObjectId = Random.Default.nextLong(),
                    stackTraceSerialNumber = 0,
                    numberOfElements = 1,
                    arrayElementType = Type.BYTE,
                    byteArray = ByteArray(1))
                }))
          else -> record
        }
      })

fun Profile.Record.process(index: Index): Profile.Record? =
  this
    .run {
      if (this is StringConstant)
        when (string) {
          "java/lang/Class" -> this
          "[B", "[S", "[I", "[J", "[F", "[D", "[Z", "[C" -> this
          "[Ljava/lang/Object;" -> copy(string = "kotlin/Array")
          else -> when {
            true -> copy(string = "xxx" + string)
            else -> this
          }
        }
      else this
    }.takeIf {
      false
        .or(it is StringConstant)
        .or(it is LoadClass)
        .or(it is HeapDump)
        .or(it is HeapDumpSection)
        .or(it is HeapDumpEnd)
    }

fun HeapDump.Record.process(index: Index): HeapDump.Record? =
  this
    .run {
      when (this) {
        is ClassDump -> {
          copy(instanceFields = listOf(), staticFields = listOf(), constants = listOf())
          val classObjectId = classObjectId
          val loadClass = index.classObjectIdToLoadClassMap[classObjectId]!!
          val className = index.idToStringMap[loadClass.classNameStringId]!!
          when (className) {
            "java/lang/Class", "[B" -> this
            else -> null
          }
        }
        is PrimitiveArrayDump -> {
          if (arrayElementType == Type.BYTE) copy(byteArray = ByteArray(numberOfElements))
          else this
        }
        else -> this
      }
    }
    .takeIf {
      false
        .or(it is ClassDump)
        //.or(it is InstanceDump)
        //.or(it is PrimitiveArrayDump && it.arrayElementType == Type.BYTE && it.numberOfElements in 20..50)
        //.or(it is ObjectArrayDump)
    }

