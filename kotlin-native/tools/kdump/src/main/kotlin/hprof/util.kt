package hprof

val Profile.heapDumpRecordSequence: Sequence<HeapDump.Record> get() =
  records.asSequence().flatMap { it.heapDumpRecordSequence }

val Profile.Record.heapDumpRecordSequence: Sequence<HeapDump.Record> get() =
  when (this) {
    is HeapDump -> records.asSequence()
    is HeapDumpSection -> records.asSequence()
    else -> sequenceOf()
  }

fun Profile.toSingleHeapDump(): Profile =
  copy(records =
    records
      .asSequence()
      .filter { it !is HeapDump && it !is HeapDumpSection && it !is HeapDumpEnd }
      .toList()
      .plus(HeapDump(heapDumpRecordSequence.toList())))
