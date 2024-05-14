package kdump.hprof

import kdump.*
import hprof.IdSize as HProfIdSize
import hprof.Profile as HProfProfile
import hprof.HeapDump as HProfHeapDump
import hprof.StringConstant as HProfStringConstant
import hprof.LoadClass as HProfLoadClass
import hprof.StackTrace as HProfStackTrace
import hprof.StackFrame as HProfStackFrame
import hprof.StartThread as HProfStartThread
import hprof.ClassDump as HProfClassDump
import hprof.InstanceDump as HProfInstanceDump
import hprof.RootJavaFrame as HProfRootJavaFrame
import hprof.RootThreadObject as HProfRootThreadObject
import hprof.RootJniGlobal as HProfRootJniGlobal
import hprof.RootStickyClass as HProfRootStickyClass
import hprof.PrimitiveArrayDump as HProfPrimitiveArrayDump
import hprof.ObjectArrayDump as HProfObjectArrayDump
import hprof.InstanceField as HProfInstanceField
import hprof.Type as HProfType
import hprof.size as hprofSize
import java.io.*
import io.*
import base.*

const val ADD_JAVA_LANG_STRINGS = true

private val hprofIdSize = HProfIdSize.LONG

private val HProfType.size: Int get() = hprofSize(hprofIdSize)

fun MemoryDump.newConverter(): Converter =
  Converter(
    endianness = endianness,
    idSize = idSize,
    idToItemMap = idToItemMap,
    profileTime = System.currentTimeMillis(),
  )

fun MemoryDump.toHProfProfile(): HProfProfile =
  newConverter().also { it.add(this) }.buildProfile()

data class Converter(
  val endianness: Endianness,
  val idSize: IdSize,
  val idToItemMap: Map<Long, Item>,
  val profileTime: Long,
  val idToHProfIdMutableMap: MutableMap<Long, Long> = mutableMapOf(),
  val stringToIdMutableMap: MutableMap<String, Long> = mutableMapOf(),
  val idToStringMutableMap: MutableMap<Long, String> = mutableMapOf(),
  val hprofProfileRecords: MutableList<HProfProfile.Record> = mutableListOf(),
  val hprofHeapDumpRecords: MutableList<HProfHeapDump.Record> = mutableListOf(),
  val extraClassObjectIds: MutableMap<String, Long> = mutableMapOf(),
  val kotlinStringIdToJavaLangStringIdMutableMap: MutableMap<Long, Long> = mutableMapOf(),
  var lastClassSerialNumber: Int = 0,
  val threadIdToSerialNumberMap: MutableMap<Long, Int> = mutableMapOf(),
  var nextFreeHProfObjectAddress: Long = 0x20000000L,
  val syntheticClassNames: MutableSet<String> = mutableSetOf(),
) {
  fun size(type: RuntimeType): Int = type.size(idSize)

  fun hprofInstanceSize(type: Type): Int =
    type.forceFields.fold(0) { acc, field -> acc + field.type.hprofTypes.map { it.size }.sum() }

  fun getId(byteArray: ByteArray, index: Int): Long =
    when (idSize) {
      IdSize.BITS_8 -> byteArray.getByte(index).toLong().and(0xffL)
      IdSize.BITS_16 -> byteArray.getShort(index, endianness).toLong().and(0xffffL)
      IdSize.BITS_32 -> byteArray.getInt(index, endianness).toLong().and(0xffffffffL)
      IdSize.BITS_64 -> byteArray.getLong(index, endianness)
    }

  fun writeHProfObjectValue(outputStream: OutputStream, byteArray: ByteArray, index: Int) {
    outputStream.writeLong(hprofObjectReferenceId(getId(byteArray, index)), Endianness.BIG)
  }

  fun writeHProfValue(
    outputStream: OutputStream,
    byteArray: ByteArray,
    index: Int,
    runtimeType: RuntimeType,
  ) {
    when (runtimeType) {
      RuntimeType.OBJECT ->
        writeHProfObjectValue(outputStream, byteArray, index)

      RuntimeType.INT_8 ->
        outputStream.writeByte(byteArray.get(index))

      RuntimeType.INT_16 ->
        outputStream.writeShort(byteArray.getShort(index, endianness), Endianness.BIG)

      RuntimeType.INT_32 ->
        outputStream.writeInt(byteArray.getInt(index, endianness), Endianness.BIG)

      RuntimeType.INT_64 ->
        outputStream.writeLong(byteArray.getLong(index, endianness), Endianness.BIG)

      RuntimeType.FLOAT_32 ->
        outputStream.writeFloat(byteArray.getFloat(index, endianness), Endianness.BIG)

      RuntimeType.FLOAT_64 ->
        outputStream.writeDouble(byteArray.getDouble(index, endianness), Endianness.BIG)

      RuntimeType.NATIVE_PTR ->
        // TODO: Convert to ID.
        outputStream.writeLong(byteArray.getLong(index, endianness), Endianness.BIG)

      RuntimeType.BOOLEAN ->
        outputStream.writeByte(byteArray.get(index))

      RuntimeType.VECTOR_128 ->
        when (endianness) {
          Endianness.BIG -> {
            outputStream.writeInt(byteArray.getInt(index + 0, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 4, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 8, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 12, endianness), Endianness.BIG)
          }

          Endianness.LITTLE -> {
            outputStream.writeInt(byteArray.getInt(index + 12, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 8, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 4, endianness), Endianness.BIG)
            outputStream.writeInt(byteArray.getInt(index + 0, endianness), Endianness.BIG)
          }
        }
    }
  }

  fun writeHProfValue(outputStream: OutputStream, byteArray: ByteArray, field: Field) =
    writeHProfValue(outputStream, byteArray, field.offset, field.type)

  fun writeHProfValues(outputStream: OutputStream, byteArray: ByteArray, fields: List<Field>) =
    fields.forEach { field ->
      writeHProfValue(outputStream, byteArray, field)
    }

  fun writeHProfFieldValues(outputStream: OutputStream, byteArray: ByteArray, type: Type) {
    writeHProfValues(outputStream, byteArray, directFields(type))

    superType(type)?.let { superType ->
      writeHProfFieldValues(outputStream, byteArray, superType)
    }
  }

  fun writeHProfArray(
    outputStream: OutputStream,
    byteArray: ByteArray,
    offset: Int,
    count: Int,
    elementType: RuntimeType,
  ) {
    size(elementType).let { elementSize ->
      repeat(count) { index ->
        writeHProfValue(outputStream, byteArray, offset + index * elementSize, elementType)
      }
    }
  }

  fun id(string: String): Long =
    stringToIdMutableMap[string] ?: stringToIdMutableMap
      .size
      .toLong()
      .inc()
      .times(8)
      .or(0x100000000L)
      .also { id ->
        stringToIdMutableMap[string] = id
        idToStringMutableMap[id] = string
        hprofProfileRecords.add(HProfStringConstant(id, string))
      }

  fun hprofObjectId(id: Long): Long =
    if (id == 0L) 0L
    else idToHProfIdMutableMap[id]
      ?: newHProfObjectId(item(id).size(idSize)!!).also { idToHProfIdMutableMap[id] = it }

  fun extraClassObjectId(className: String): Long =
    extraClassObjectIds.getOrPut(className) {
      newHProfObjectId(64)
    }

  fun getReferenceId(id: Long): Long = id

  fun hprofObjectReferenceId(id: Long): Long =
    hprofObjectId(getReferenceId(id)).let { hprofId ->
      kotlinStringIdToJavaLangStringIdMutableMap[hprofId] ?: hprofId
    }

  fun newHProfObjectId(size: Int): Long =
    nextFreeHProfObjectAddress.also {
      nextFreeHProfObjectAddress = it.plus(size.align(8))
    }

  fun hprofClassObjectId(type: Type): Long =
    type.body.let { typeBody ->
      when (typeBody) {
        is Type.Body.Array ->
          extraClassObjectId(
            when (typeBody.extra!!.elementType) {
              RuntimeType.OBJECT -> ClassName.Array.OBJECT
              RuntimeType.INT_8 -> ClassName.Array.BYTE
              RuntimeType.INT_16 -> ClassName.Array.SHORT
              RuntimeType.INT_32 -> ClassName.Array.INT
              RuntimeType.INT_64 -> ClassName.Array.LONG
              RuntimeType.FLOAT_32 -> ClassName.Array.FLOAT
              RuntimeType.FLOAT_64 -> ClassName.Array.DOUBLE
              RuntimeType.NATIVE_PTR -> ClassName.Array.LONG
              RuntimeType.BOOLEAN -> ClassName.Array.BOOLEAN
              RuntimeType.VECTOR_128 -> ClassName.Array.INT
            }
          )

        is Type.Body.Object ->
          when {
            else -> hprofObjectId(type.id)
          }
      }
    }

  fun hprofSuperClassObjectId(type: Type): Long =
    superType(type)
      ?.let { hprofClassObjectId(it) }
      ?: extraClassObjectId(ClassName.OBJECT)

  fun nextClassSerialNumber(): Int =
    lastClassSerialNumber.inc().also { lastClassSerialNumber = it }

  fun threadSerialNumber(threadId: Long): Int =
    threadIdToSerialNumberMap.getOrPut(threadId) {
      threadIdToSerialNumberMap.size.inc()
    }

  fun hprofStringConstants(): List<HProfStringConstant> =
    idToStringMutableMap.map { (id, string) -> HProfStringConstant(id, string) }

  fun type(id: Long): Type = item(id) as Type

  fun item(id: Long): Item =
    idToItemMap[id] ?: throw IllegalArgumentException("No item for id: $id")

  fun superType(type: Type): Type? =
    type.superTypeId.takeIf { it != 0L }?.let { type(it) }

  fun buildProfile(): HProfProfile =
    HProfProfile(
      idSize = hprofIdSize,
      time = profileTime,
      records = buildList {
        addAll(hprofProfileRecords)
        add(HProfHeapDump(hprofHeapDumpRecords))
      })

  fun add(memoryDump: MemoryDump) {
    syntheticClassNames.add(ClassName.CLASS)

    addSyntheticClass(ClassName.OBJECT)

    addSyntheticClass(ClassName.Array.BOOLEAN, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.CHAR, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.BYTE, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.SHORT, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.INT, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.LONG, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.FLOAT, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.DOUBLE, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.Array.OBJECT, extraClassObjectId(ClassName.OBJECT))

    addSyntheticClass(ClassName.CLASS, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.CLASS_LOADER, extraClassObjectId(ClassName.OBJECT))
    addSyntheticClass(ClassName.THREAD, extraClassObjectId(ClassName.OBJECT))

    addSyntheticClass(
      ClassName.STRING,
      extraClassObjectId(ClassName.OBJECT),
      listOf(
        HProfInstanceField(
          nameStringId = id("value"),
          type = HProfType.OBJECT
        )
      )
    )

    addSyntheticClass(
      ClassName.EXTRA_OBJECT,
      extraClassObjectId(ClassName.OBJECT),
      listOf(
        HProfInstanceField(
          nameStringId = id("baseObject"),
          type = HProfType.OBJECT
        ),
        HProfInstanceField(
          nameStringId = id("associatedObject"),
          type = HProfType.LONG
        )
      )
    )

    if (ADD_JAVA_LANG_STRINGS) {
      addJavaLangStringIds(memoryDump)
    }

    memoryDump.items.forEach { add(it) }
  }

  fun addJavaLangStringIds(memoryDump: MemoryDump) {
    memoryDump.items.forEach { item ->
      if (item is ArrayItem) {
        val type = type(item.typeId)
        if (type.isKotlinString) {
          val hprofObjectId = hprofObjectId(item.id)
          val hprofStringObjectId = newHProfObjectId(idSize.byteCount * 2)
          kotlinStringIdToJavaLangStringIdMutableMap[hprofObjectId] = hprofStringObjectId
        }
      }
    }
  }

  fun addJavaLangStringRecords(arrayItem: ArrayItem, hprofJavaLangStringId: Long) {
    hprofHeapDumpRecords.add(
      HProfInstanceDump(
        objectId = hprofJavaLangStringId,
        stackTraceSerialNumber = 0,
        classObjectId = extraClassObjectId(ClassName.STRING),
        ByteArrayOutputStream()
          .apply { writeLong(hprofObjectId(arrayItem.id), Endianness.BIG) }
          .toByteArray()))
  }

  fun addSyntheticClass(
    className: String,
    superClassObjectId: Long = 0,
    instanceFields: List<HProfInstanceField> = listOf(),
  ) {
    val classObjectId = extraClassObjectId(className)
    val instanceSize = instanceFields.map { it.type.size }.sum()

    hprofProfileRecords.add(
      HProfLoadClass(
        classSerialNumber = nextClassSerialNumber(),
        classObjectId = classObjectId,
        stackTraceSerialNumber = 0,
        classNameStringId = id(className)
      )
    )

    hprofHeapDumpRecords.add(
      HProfClassDump(
        classObjectId = classObjectId,
        superClassObjectId = superClassObjectId,
        instanceSize = instanceSize,
        instanceFields = instanceFields
      )
    )
  }

  fun add(item: Item) {
    when (item) {
      is Type -> addItem(item)
      is ObjectItem -> addItem(item)
      is ArrayItem -> addItem(item)
      is ExtraObject -> addItem(item)
      is Thread -> addItem(item)
      is GlobalRoot -> addItem(item)
      is ThreadRoot -> addItem(item)
    }
  }

  fun addItem(extraObject: ExtraObject) {
    hprofHeapDumpRecords.add(
      HProfInstanceDump(
        objectId = hprofObjectId(extraObject.id),
        stackTraceSerialNumber = 0,
        classObjectId = extraClassObjectId(ClassName.EXTRA_OBJECT),
        ByteArrayOutputStream()
          .apply {
            writeLong(hprofObjectReferenceId(extraObject.baseObjectId), Endianness.BIG)
            writeLong(extraObject.associatedObjectId, Endianness.BIG)
          }
          .toByteArray()))
  }

  fun addItem(thread: Thread) {
    val threadObjectId = hprofObjectId(thread.id)
    val threadSerialNumber = threadSerialNumber(thread.id)
    val stackTraceSerialNumber = threadSerialNumber // same as thread serial number for now

    hprofProfileRecords.add(
      HProfStartThread(
        threadSerialNumber = threadSerialNumber,
        threadObjectId = threadObjectId,
        threadNameStringId = id("thread-$threadSerialNumber"),
      )
    )

    val stackFrameId = newHProfObjectId(32) // Some approximation
    hprofProfileRecords.add(
      HProfStackFrame(
        stackFrameId = stackFrameId,
        methodNameStringId = id("start"),
        methodSignatureStringId = id("()V"),
      )
    )

    hprofProfileRecords.add(
      HProfStackTrace(
        serialNumber = stackTraceSerialNumber,
        threadSerialNumber = threadSerialNumber,
        stackFrameIds = longArrayOf(stackFrameId)
      )
    )

    hprofHeapDumpRecords.add(
      HProfInstanceDump(
        objectId = threadObjectId,
        classObjectId = extraClassObjectId(ClassName.THREAD)))

    hprofHeapDumpRecords.add(
      HProfRootThreadObject(
        threadObjectId = threadObjectId,
        threadSerialNumber = threadSerialNumber,
        stackTraceSerialNumber = stackTraceSerialNumber
      )
    )
  }

  fun addItem(globalRoot: GlobalRoot) {
    hprofHeapDumpRecords.add(
      HProfRootJniGlobal(
        objectId = hprofObjectReferenceId(globalRoot.objectId),
        refId = hprofObjectReferenceId(globalRoot.objectId)
      )
    )
  }

  fun addItem(threadRoot: ThreadRoot) {
    hprofHeapDumpRecords.add(
      HProfRootJavaFrame(
        objectId = hprofObjectReferenceId(threadRoot.objectId),
        threadSerialNumber = threadSerialNumber(threadRoot.threadId))
    )
  }

  fun addItem(type: Type) {
    when (type.body) {
      is Type.Body.Array -> {
        // All array types are synthesized.
      }

      is Type.Body.Object -> {
        hprofProfileRecords.add(hprofLoadClass(type))

        hprofHeapDumpRecords.add(
          HProfRootStickyClass(
            objectId = hprofClassObjectId(type)
          )
        )

        hprofHeapDumpRecords.add(hprofClassDump(type))
      }
    }
  }

  fun hprofLoadClass(type: Type): HProfLoadClass {
    val className = type.hprofClassName.let {
      it.takeIf { !syntheticClassNames.contains(it) } ?: "$it\$Kotlin"
    }

    return HProfLoadClass(
      classSerialNumber = nextClassSerialNumber(),
      classObjectId = hprofClassObjectId(type),
      classNameStringId = id(className)
    )
  }

  fun hprofClassDump(type: Type): HProfClassDump =
    type.body.let { body ->
      when (body) {
        is Type.Body.Object ->
          HProfClassDump(
            classObjectId = hprofClassObjectId(type),
            superClassObjectId = hprofSuperClassObjectId(type),
            instanceSize = hprofInstanceSize(type),
            instanceFields = hprofInstanceFields(type))

        is Type.Body.Array ->
          throw IllegalStateException("Array classes are synthesized.")
      }
    }

  fun hprofInstanceFields(type: Type): List<HProfInstanceField> =
    hprofInstanceFields(directFields(type))

  fun hprofInstanceFields(fields: List<Field>): List<HProfInstanceField> =
    fields.flatMap { hprofInstanceFields(it) }

  fun hprofInstanceFields(field: Field): List<HProfInstanceField> =
    field.type.hprofTypes.map { hprofType ->
      HProfInstanceField(
        nameStringId = id(field.name), // TODO: Add prefix to each vector component
        type = hprofType
      )
    }

  fun directFields(type: Type): List<Field> =
    type.forceFields.drop(superType(type)?.forceFields?.size ?: 0)

  fun addItem(objectItem: ObjectItem) {
    hprofHeapDumpRecords.add(hprofDumpRecord(objectItem))
  }

  fun addItem(arrayItem: ArrayItem) {
    if (ADD_JAVA_LANG_STRINGS) {
      val hprofObjectId = hprofObjectId(arrayItem.id)
      val hprofStringId = kotlinStringIdToJavaLangStringIdMutableMap[hprofObjectId]
      if (hprofStringId != null) {
        addJavaLangStringRecords(arrayItem, hprofStringId)
      }
    }
    hprofHeapDumpRecords.add(hprofDumpRecord(arrayItem))
  }

  fun hprofDumpRecord(objectItem: ObjectItem): HProfHeapDump.Record {
    val objectId = hprofObjectId(objectItem.id)
    val type = type(objectItem.typeId)
    val byteArray = objectItem.byteArray
    return hprofInstanceDump(objectId, type, byteArray)
  }

  fun hprofDumpRecord(arrayItem: ArrayItem): HProfHeapDump.Record {
    val objectId = hprofObjectId(arrayItem.id)
    val type = type(arrayItem.typeId)
    val count = arrayItem.count
    val byteArray = arrayItem.byteArray

    val offset = 0

    return if (type.packageName != "kotlin") {
      throw IllegalArgumentException("Unknown array type package name: ${type.packageName}")
    } else when (type.relativeName) {
      "Array" ->
        hprofObjectArrayDump(objectId, hprofClassObjectId(type), byteArray, offset, count)

      else -> {
        val (runtimeElementType, hprofElementType) =
          type.relativeName.primitiveArrayClassNameToElementTypePair()
        hprofPrimitiveArrayDump(
          objectId,
          hprofElementType,
          byteArray,
          offset,
          count,
          runtimeElementType
        )
      }
    }
  }

  fun hprofInstanceDump(
    hprofObjectId: Long,
    type: Type,
    byteArray: ByteArray,
  ): HProfInstanceDump {
    return HProfInstanceDump(
      objectId = hprofObjectId,
      stackTraceSerialNumber = 0,
      classObjectId = hprofClassObjectId(type),
      ByteArrayOutputStream()
        .apply { writeHProfFieldValues(this, byteArray, type) }
        .toByteArray())
  }

  fun hprofPrimitiveArrayDump(
    hprofObjectId: Long,
    hprofElementType: HProfType,
    byteArray: ByteArray,
    offset: Int,
    count: Int,
    elementRuntimeType: RuntimeType,
  ): HProfPrimitiveArrayDump {
    return HProfPrimitiveArrayDump(
      arrayObjectId = hprofObjectId,
      stackTraceSerialNumber = 0,
      numberOfElements = count,
      arrayElementType = hprofElementType,
      ByteArrayOutputStream()
        .apply { writeHProfArray(this, byteArray, offset, count, elementRuntimeType) }
        .toByteArray())
  }

  fun hprofObjectArrayDump(
    hprofObjectId: Long,
    hprofArrayClassObjectId: Long,
    byteArray: ByteArray,
    offset: Int,
    count: Int,
  ): HProfObjectArrayDump {
    return HProfObjectArrayDump(
      arrayObjectId = hprofObjectId,
      numberOfElements = count,
      arrayClassObjectId = hprofArrayClassObjectId,
      byteArray = ByteArrayOutputStream()
        .apply { writeHProfArray(this, byteArray, offset, count, RuntimeType.OBJECT) }
        .toByteArray())
  }
}
