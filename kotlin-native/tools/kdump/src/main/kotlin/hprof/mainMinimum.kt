package hprof

import base.*
import java.io.*

object StringId {
  object Class {
    const val JAVA_LANG_CLASS = 1L
    const val JAVA_LANG_OBJECT = 2L
    const val JAVA_LANG_STRING = 3L
    const val JAVA_LANG_REF_REFERENCE = 4L
    const val JAVA_LANG_REF_SOFT_REFERENCE = 5L
    const val JAVA_LANG_REF_WEAK_REFERENCE = 6L

    object PrimitiveArray {
      const val BYTE = 1000L
      const val SHORT = 1001L
      const val INT = 1002L
      const val LONG = 1003L
      const val FLOAT = 1004L
      const val DOUBLE = 1005L
      const val BOOLEAN = 1006L
      const val CHAR = 1007L
    }
  }

  const val VALUE = 10001L
  const val REFERENT = 10002L
}

object ObjectId {
  object Class {
    const val JAVA_LANG_CLASS = 31L
    const val JAVA_LANG_OBJECT = 32L
    const val JAVA_LANG_STRING = 33L
    const val JAVA_LANG_REF_REFERENCE = 34L
    const val JAVA_LANG_REF_SOFT_REFERENCE = 35L
    const val JAVA_LANG_REF_WEAK_REFERENCE = 36L

    object PrimitiveArray {
      const val BYTE = 31000L
      const val SHORT = 31001L
      const val INT = 31002L
      const val LONG = 31003L
      const val FLOAT = 31004L
      const val DOUBLE = 31005L
      const val BOOLEAN = 31006L
      const val CHAR = 31007L
    }
  }

  object Instance {
    const val OBJECT_A = 1L
  }
}

object SerialNumber {
  object Class {
    const val JAVA_LANG_CLASS = 1
    const val JAVA_LANG_OBJECT = 2
    const val JAVA_LANG_STRING = 3
    const val JAVA_LANG_REF_REFERENCE = 4
    const val JAVA_LANG_REF_SOFT_REFERENCE = 5
    const val JAVA_LANG_REF_WEAK_REFERENCE = 6

    object PrimitiveArray {
      const val BYTE = 1000
      const val SHORT = 1001
      const val INT = 1002
      const val LONG = 1003
      const val FLOAT = 1004
      const val DOUBLE = 1005
      const val BOOLEAN = 1006
      const val CHAR = 1007
    }
  }
}


fun main() {
  val profile =
    Profile(
      idSize = IdSize.LONG,
      time = 12345678L,
      records = listOf(
        StringConstant(StringId.Class.JAVA_LANG_CLASS, "java/lang/Class"),
        //StringConstant(StringId.Class.JAVA_LANG_OBJECT, "java/lang/Object"),
        // StringConstant(StringId.Class.JAVA_LANG_STRING, "java/lang/String"),
        // StringConstant(StringId.Class.JAVA_LANG_REF_REFERENCE, "java/lang/ref/Reference"),
        // StringConstant(StringId.Class.JAVA_LANG_REF_SOFT_REFERENCE, "java/lang/ref/SoftReference"),
        // StringConstant(StringId.Class.JAVA_LANG_REF_WEAK_REFERENCE, "java/lang/ref/WeakReference"),

        StringConstant(StringId.Class.PrimitiveArray.BYTE, "[B"),
        // StringConstant(StringId.Class.PrimitiveArray.SHORT, "[S"),
        StringConstant(StringId.Class.PrimitiveArray.INT, "[I"),
        // StringConstant(StringId.Class.PrimitiveArray.LONG, "[J"),
        // StringConstant(StringId.Class.PrimitiveArray.FLOAT, "[F"),
        // StringConstant(StringId.Class.PrimitiveArray.DOUBLE, "[D"),
        // StringConstant(StringId.Class.PrimitiveArray.BOOLEAN, "[Z"),
        // StringConstant(StringId.Class.PrimitiveArray.CHAR, "[C"),

        // StringConstant(StringId.VALUE, "value"),
        // StringConstant(StringId.REFERENT, "referent"),

        LoadClass(
          classSerialNumber = SerialNumber.Class.JAVA_LANG_CLASS,
          classObjectId = ObjectId.Class.JAVA_LANG_CLASS,
          classNameStringId = StringId.Class.JAVA_LANG_CLASS),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.JAVA_LANG_OBJECT,
        //   classObjectId = ObjectId.Class.JAVA_LANG_OBJECT,
        //   classNameStringId = StringId.Class.JAVA_LANG_OBJECT),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.JAVA_LANG_STRING,
        //   classObjectId = ObjectId.Class.JAVA_LANG_STRING,
        //   classNameStringId = StringId.Class.JAVA_LANG_STRING),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.JAVA_LANG_REF_REFERENCE,
        //   classObjectId = ObjectId.Class.JAVA_LANG_REF_REFERENCE,
        //   classNameStringId = StringId.Class.JAVA_LANG_REF_REFERENCE),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.JAVA_LANG_REF_SOFT_REFERENCE,
        //   classObjectId = ObjectId.Class.JAVA_LANG_REF_SOFT_REFERENCE,
        //   classNameStringId = StringId.Class.JAVA_LANG_REF_SOFT_REFERENCE),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.JAVA_LANG_REF_WEAK_REFERENCE,
        //   classObjectId = ObjectId.Class.JAVA_LANG_REF_WEAK_REFERENCE,
        //   classNameStringId = StringId.Class.JAVA_LANG_REF_WEAK_REFERENCE),

        LoadClass(
          classSerialNumber = SerialNumber.Class.PrimitiveArray.BYTE,
          classObjectId = ObjectId.Class.PrimitiveArray.BYTE,
          classNameStringId = StringId.Class.PrimitiveArray.BYTE),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.SHORT,
        //   classObjectId = ObjectId.Class.PrimitiveArray.SHORT,
        //   classNameStringId = StringId.Class.PrimitiveArray.SHORT),
        LoadClass(
          classSerialNumber = SerialNumber.Class.PrimitiveArray.INT,
          classObjectId = ObjectId.Class.PrimitiveArray.INT,
          classNameStringId = StringId.Class.PrimitiveArray.INT),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.LONG,
        //   classObjectId = ObjectId.Class.PrimitiveArray.LONG,
        //   classNameStringId = StringId.Class.PrimitiveArray.LONG),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.FLOAT,
        //   classObjectId = ObjectId.Class.PrimitiveArray.FLOAT,
        //   classNameStringId = StringId.Class.PrimitiveArray.FLOAT),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.DOUBLE,
        //   classObjectId = ObjectId.Class.PrimitiveArray.DOUBLE,
        //   classNameStringId = StringId.Class.PrimitiveArray.DOUBLE),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.BOOLEAN,
        //   classObjectId = ObjectId.Class.PrimitiveArray.BOOLEAN,
        //   classNameStringId = StringId.Class.PrimitiveArray.BOOLEAN),
        // LoadClass(
        //   classSerialNumber = SerialNumber.Class.PrimitiveArray.CHAR,
        //   classObjectId = ObjectId.Class.PrimitiveArray.CHAR,
        //   classNameStringId = StringId.Class.PrimitiveArray.CHAR),

        HeapDump(
          records = listOf(
            // RootStickyClass(objectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // RootStickyClass(objectId = ObjectId.Class.JAVA_LANG_STRING),
            // RootStickyClass(objectId = ObjectId.Class.JAVA_LANG_REF_REFERENCE),
            // RootStickyClass(objectId = ObjectId.Class.JAVA_LANG_REF_SOFT_REFERENCE),
            // RootStickyClass(objectId = ObjectId.Class.JAVA_LANG_REF_WEAK_REFERENCE),

            ClassDump(
              classObjectId = ObjectId.Class.JAVA_LANG_CLASS,
              superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),

            // ClassDump(
            //   classObjectId = ObjectId.Class.JAVA_LANG_OBJECT),

            // ClassDump(
            //   classObjectId = ObjectId.Class.JAVA_LANG_STRING,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT,
            //   instanceSize = 8,
            //   instanceFields = listOf(
            //     InstanceField(
            //       nameStringId = StringId.VALUE,
            //       type = Type.OBJECT))),

            // ClassDump(
            //   classObjectId = ObjectId.Class.JAVA_LANG_REF_REFERENCE,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT,
            //   instanceSize = 8,
            //   instanceFields = listOf(
            //     InstanceField(
            //       nameStringId = StringId.REFERENT,
            //       type = Type.OBJECT))),
            // ClassDump(
            //   classObjectId = ObjectId.Class.JAVA_LANG_REF_SOFT_REFERENCE,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_REF_REFERENCE),
            // ClassDump(
            //   classObjectId = ObjectId.Class.JAVA_LANG_REF_WEAK_REFERENCE,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_REF_REFERENCE),

            ClassDump(
              classObjectId = ObjectId.Class.PrimitiveArray.BYTE,
              superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),

            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.SHORT,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            ClassDump(
              classObjectId = ObjectId.Class.PrimitiveArray.INT,
              superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.LONG,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.FLOAT,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.DOUBLE,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.BOOLEAN,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),
            // ClassDump(
            //   classObjectId = ObjectId.Class.PrimitiveArray.CHAR,
            //   superClassObjectId = ObjectId.Class.JAVA_LANG_OBJECT),

            //PrimitiveArrayDump(arrayObjectId = 0x1000001L),
            //PrimitiveArrayDump(arrayObjectId = 0x1000002L),
            //PrimitiveArrayDump(arrayObjectId = 0x1000003L),
            //PrimitiveArrayDump(arrayObjectId = 0x1000004L),
            //PrimitiveArrayDump(arrayObjectId = 0x1000005L),
            //PrimitiveArrayDump(arrayObjectId = 0x1000006L),
            // PrimitiveArrayDump(arrayObjectId = 0x1000007L),
            // PrimitiveArrayDump(arrayObjectId = 0x1000008L),
            // PrimitiveArrayDump(arrayObjectId = 0x1000009L),
            //PrimitiveArrayDump(arrayObjectId = 0x100000aL),
            PrimitiveArrayDump(arrayObjectId = 0x100000bL, numberOfElements = 24),

            PrimitiveArrayDump(
              arrayObjectId = 0x100000cL,
              arrayElementType = Type.INT,
              numberOfElements = 500)))))

  File("minimum.hprof").write(profile)
  File("minimum.hprof").readProfile().dump()
}
