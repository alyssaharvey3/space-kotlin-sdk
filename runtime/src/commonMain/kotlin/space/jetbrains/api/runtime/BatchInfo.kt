package space.jetbrains.api.runtime

import space.jetbrains.api.runtime.PropertyValue.Value

public class BatchInfo(
    offset: PropertyValue<String?>,
    batchSize: PropertyValue<Int>
) {
    public val offset: String? by offset
    public val batchSize: Int by batchSize

    public constructor(offset: String?, batchSize: Int) : this(Value(offset), Value(batchSize))
}

public object BatchInfoStructure : TypeStructure<BatchInfo>(isRecord = false) {
    private val offset: Property<String?> by string().nullable()
    private val batchSize: Property<Int> by int()

    override fun deserialize(context: DeserializationContext): BatchInfo = BatchInfo(
        offset = offset.deserialize(context),
        batchSize = batchSize.deserialize(context)
    )

    override fun serialize(value: BatchInfo): JsonValue = jsonObject(listOfNotNull(
        offset.serialize(value.offset),
        batchSize.serialize(value.batchSize)
    ))
}
