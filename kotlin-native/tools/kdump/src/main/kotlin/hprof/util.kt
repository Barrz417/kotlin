package hprof

val Profile.heapDumpRecordSequence: Sequence<HeapDump.Record> get() =
  records.asSequence().flatMap { it.heapDumpRecordSequence }

val Profile.Record.heapDumpRecordSequence: Sequence<HeapDump.Record> get() =
  when (this) {
    is HeapDump -> records.asSequence()
    is HeapDumpSection -> records.asSequence()
    else -> sequenceOf()
  }
