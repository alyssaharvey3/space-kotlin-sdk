package space.jetbrains.api.runtime

import space.jetbrains.api.runtime.Type.NumberType.IntType
import space.jetbrains.api.runtime.Type.NumberType.LongType
import space.jetbrains.api.runtime.Type.PrimitiveType.StringType

sealed class Type<T> {
    abstract fun deserialize(context: DeserializationContext<*>): T
    abstract fun serialize(value: T): JsonValue?

    sealed class NumberType<T : Number> : Type<T>() {
        protected abstract fun fromNumber(number: Number): T

        override fun deserialize(context: DeserializationContext<*>): T {
            return fromNumber(context.requireJson().asNumber(context.link))
        }

        override fun serialize(value: T): JsonValue = jsonNumber(value)

        object ByteType : NumberType<Byte>() {
            override fun fromNumber(number: Number): Byte = number.toByte()
        }

        object ShortType : NumberType<Short>() {
            override fun fromNumber(number: Number): Short = number.toShort()
        }

        object IntType : NumberType<Int>() {
            override fun fromNumber(number: Number): Int = number.toInt()
        }

        object LongType : NumberType<Long>() {
            override fun fromNumber(number: Number): Long = number.toLong()
        }

        object FloatType : NumberType<Float>() {
            override fun fromNumber(number: Number): Float = number.toFloat()
        }

        object DoubleType : NumberType<Double>() {
            override fun fromNumber(number: Number): Double = number.toDouble()
        }
    }

    sealed class PrimitiveType<T : Any> : Type<T>() {
        object BooleanType : PrimitiveType<Boolean>() {
            override fun deserialize(context: DeserializationContext<*>): Boolean = context.requireJson().asBoolean(context.link)
            override fun serialize(value: Boolean): JsonValue = jsonBoolean(value)
        }

        object StringType : PrimitiveType<String>() {
            override fun deserialize(context: DeserializationContext<*>): String = context.requireJson().asString(context.link)
            override fun serialize(value: String): JsonValue = jsonString(value)
        }

        object DateType : PrimitiveType<SDate>() {
            override fun deserialize(context: DeserializationContext<*>): SDate {
                return SDate(StringType.deserialize(context.child("iso")))
            }

            override fun serialize(value: SDate): JsonValue {
                return jsonObject("iso" to jsonString(value.iso))
            }
        }

        object DateTimeType : PrimitiveType<SDateTime>() {
            override fun deserialize(context: DeserializationContext<*>): SDateTime {
                return SDateTime(LongType.deserialize(context.child("timestamp")))
            }

            override fun serialize(value: SDateTime): JsonValue {
                return jsonObject("timestamp" to jsonNumber(value.timestamp))
            }
        }
    }

    class Nullable<T : Any>(val type: Type<T>) : Type<T?>() {
        override fun deserialize(context: DeserializationContext<*>): T? {
            return if (context.json != null && !context.json.isNull()) {
                type.deserialize(context)
            }
            else null
        }

        override fun serialize(value: T?): JsonValue {
            return value?.let { type.serialize(it) } ?: jsonNull()
        }
    }

    class Optional<T>(val type: Type<T>) : Type<Option<T>>() {
        override fun deserialize(context: DeserializationContext<*>): Option<T> {
            return if (context.json != null) {
                Option.Value(type.deserialize(context))
            }
            else Option.None
        }

        override fun serialize(value: Option<T>): JsonValue? {
            return (value as? Option.Value)?.let { type.serialize(it.value) }
        }
    }

    class ArrayType<T>(val elementType: Type<T>) : Type<List<T>>() {
        override fun deserialize(context: DeserializationContext<*>): List<T> = context.elements().map {
            elementType.deserialize(it)
        }

        override fun serialize(value: List<T>): JsonValue {
            return jsonArray(*Array(value.size) { elementType.serialize(value[it])!! })
        }
    }

    class MapType<K, V>(private val keyType: Type<K>, val valueType: Type<V>) : Type<Map<K, V>>() {
        private val elementType = ObjectType(ApiMapEntryStructure(keyType, valueType))

        override fun deserialize(context: DeserializationContext<*>): Map<K, V> {
            return context.requireJson().arrayElements(context.link).associate { json ->
                @Suppress("UNCHECKED_CAST")
                val key = keyType.deserialize(context.child(
                    name = "<key>",
                    json = json["key"],
                    partial = keyType.partialStructure()?.let {
                        Partial(it, parent = context.partial).apply(it.defaultPartialCompact as Partial<out Any>.() -> Unit)
                    }
                ))

                key to valueType.deserialize(context.child("[$key]", json["value"], context.partial))
            }
        }

        override fun serialize(value: Map<K, V>): JsonValue {
            val entries = value.map { ApiMapEntry(it.key, it.value) }
            return jsonArray(*Array(entries.size) { elementType.serialize(entries[it]) })
        }
    }

    class BatchType<T>(val elementType: Type<T>) : Type<Batch<T>>() {
        private val arrayType = ArrayType(elementType)

        override fun deserialize(context: DeserializationContext<*>): Batch<T> {
            return Batch(
                StringType.deserialize(context.child("next")),
                Nullable(IntType).deserialize(context.child("totalCount")),
                arrayType.deserialize(context.child("data", partial = context.partial))
            )
        }

        override fun serialize(value: Batch<T>): JsonValue {
            return jsonObject(
                "next" to jsonString(value.next),
                "totalCount" to (value.totalCount?.let { jsonNumber(it) } ?: jsonNull()),
                "data" to arrayType.serialize(value.data)
            )
        }
    }

    class ObjectType<T : Any>(val structure: TypeStructure<T>) : Type<T>() {
        override fun deserialize(context: DeserializationContext<*>): T {
            context.requireJson()
            @Suppress("UNCHECKED_CAST")
            return structure.deserialize(context as DeserializationContext<in T>)
        }

        override fun serialize(value: T): JsonValue = structure.serialize(value)
    }

    class EnumType<T : Enum<T>>(private val values: List<T>) : Type<T>() {
        override fun deserialize(context: DeserializationContext<*>): T {
            val name = PrimitiveType.StringType.deserialize(context)
            return values.first { it.name == name }
        }

        override fun serialize(value: T): JsonValue = jsonString(value.name)

        companion object {
            inline operator fun <reified T : Enum<T>> invoke(): EnumType<T> = EnumType(enumValues<T>().asList())
        }
    }

    fun partialStructure(): TypeStructure<*>? = when (this) {
        is NumberType, is PrimitiveType, is EnumType -> null
        is Nullable<*> -> type.partialStructure()
        is Optional<*> -> type.partialStructure()
        is ArrayType<*> -> elementType.partialStructure()
        is MapType<*, *> -> valueType.partialStructure()
        is BatchType<*> -> elementType.partialStructure()
        is ObjectType -> structure
    }
}