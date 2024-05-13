package hprof

import base.*

data class Index(
  val stringToIdMap: Map<String, Long>,
  val idToStringMap: Map<Long, String>,
  val idToRecordMap: Map<Long, HeapDump.Record>,
  val classNameStringIdToLoadClassMap: Map<Long, LoadClass>,
  val classObjectIdToLoadClassMap: Map<Long, LoadClass>,
)

data class ProfileIndexed(
  val profile: Profile,
  val index: Index,
)

fun Profile.toIndexed() = ProfileIndexed(this, toIndex())

fun ProfileIndexed.getId(string: String): Long =
  index.stringToIdMap[string] ?:
    throw IllegalArgumentException("No ID for \"$string\"")

fun ProfileIndexed.getString(id: Long): String =
  index.idToStringMap[id] ?:
    throw IllegalArgumentException("No string for ID $id")

fun ProfileIndexed.getLoadClass(id: Long): LoadClass =
  index.classObjectIdToLoadClassMap[id] ?:
    throw IllegalArgumentException("No LoadClass for ID $id")

fun ProfileIndexed.getLoadClass(className: String): LoadClass =
  index.classNameStringIdToLoadClassMap[getId(className)] ?:
    throw IllegalArgumentException("No LoadClass for \"$className\"")

fun ProfileIndexed.getRecord(id: Long): HeapDump.Record =
  index.idToRecordMap[id] ?:
    throw IllegalArgumentException("No record for id: $id")

fun ProfileIndexed.getClassDump(className: String): ClassDump =
  getRecord(getLoadClass(className).classObjectId) as ClassDump

fun ProfileIndexed.getClassName(classDump: ClassDump): String =
  getString(getLoadClass(classDump.classObjectId).classNameStringId)

val HeapDump.Record.dumpObjectIdOrNull: Long? get() =
  when (this) {
    is ClassDump -> classObjectId
    is InstanceDump -> objectId
    is ObjectArrayDump -> arrayObjectId
    is PrimitiveArrayDump -> arrayObjectId
    else -> null
  }

fun Profile.toIndex(): Index =
  Index(
    stringToIdMap =
      records
        .asSequence()
        .filterIsInstance<StringConstant>()
        .map { it.string to it.id }
        .toMap(),
    idToStringMap =
      records
        .asSequence()
        .filterIsInstance<StringConstant>()
        .map { it.id to it.string }
        .toMap(),
    idToRecordMap =
      heapDumpRecordSequence
        .map { record ->
          record.dumpObjectIdOrNull?.let { id ->
            id to record
          }
        }
        .filterNotNull()
        .toMap(),
    classNameStringIdToLoadClassMap =
      records
        .asSequence()
        .filterIsInstance<LoadClass>()
        .associateBy { it.classNameStringId },
    classObjectIdToLoadClassMap =
      records
        .asSequence()
        .filterIsInstance<LoadClass>()
        .associateBy { it.classObjectId },
  )

fun Dumper.dump(indexed: ProfileIndexed, classDump: ClassDump) {
  dump("class dump") {
    dump1("name") {
      dump(indexed.getClassName(classDump))
    }
    dump("fields") {
      classDump.instanceFields.forEach { instanceField ->
        dump("field") {
          dump1("name") {
            dump(indexed.getString(instanceField.nameStringId))
          }
          dump1("type") {
            dump(instanceField.type.name)
          }
        }
      }
    }
  }
}
