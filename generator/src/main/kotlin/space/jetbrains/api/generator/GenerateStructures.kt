package space.jetbrains.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

private fun CodeBlock.Builder.appendPropertyDelegate(field: HA_Field, model: HttpApiEntitiesById) =
    appendPropertyDelegate(field.type, model, field.requiresOption)

private fun CodeBlock.Builder.appendPropertyDelegate(type: HA_Type, model: HttpApiEntitiesById, option: Boolean): CodeBlock.Builder {
    when (type) {
        is HA_Type.Primitive -> when (type.primitive) {
            HA_Primitive.Byte -> add("byte()")
            HA_Primitive.Short -> add("short()")
            HA_Primitive.Int -> add("int()")
            HA_Primitive.Long -> add("long()")
            HA_Primitive.Float -> add("float()")
            HA_Primitive.Double -> add("double()")
            HA_Primitive.Boolean -> add("boolean()")
            HA_Primitive.String -> add("string()")
            HA_Primitive.Date -> add("date()")
            HA_Primitive.DateTime -> add("datetime()")
        }
        is HA_Type.Array -> {
            add("list(")
            appendPropertyDelegate(type.elementType, model, false)
            add(")")
        }
        is HA_Type.Map -> {
            add("map(")
            appendPropertyDelegate(type.valueType, model, false)
            add(")")
        }
        is HA_Type.Object, is HA_Type.Dto, is HA_Type.Ref, is HA_Type.UrlParam -> {
            add("obj(")
            appendStructure(type, model)
            add(")")
        }
        is HA_Type.Enum -> {
            add("enum<%T>()", type.copy(nullable = false).kotlinPoet(model))
        }
    }.let {}

    if (type.nullable) add(".nullable()")
    if (option) add(".optional()")

    return this
}

fun generateStructures(model: HttpApiEntitiesById): List<FileSpec> {
    val fieldDescriptorsByDtoId = model.buildFieldsByDtoId()

    return model.dtoAndUrlParams.values.mapNotNull { root ->
        if (root.extends != null) return@mapNotNull null

        val rootClassName = root.getClassName()
        if (rootClassName == batchInfoType) return@mapNotNull null

        val rootStructureClassName = rootClassName.getStructureClassName()

        FileSpec.builder(rootStructureClassName.packageName, rootStructureClassName.simpleName).apply {
            indent(INDENT)

            addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "ClassName")
                    .addMember("%S", "UnusedImport")
                    .addMember("%S", "REDUNDANT_ELSE_IN_WHEN")
                    .addMember("%S", "RemoveExplicitTypeArguments")
                    .build()
            )

            root.subclasses(model).forEach { dto ->
                val dtoClassName = dto.getClassName()
                val dtoStructureClassName = dtoClassName.getStructureClassName()
                Log.info {
                    "Generating structure for '${dto.name}'"
                }
                addType(TypeSpec.objectBuilder(dtoStructureClassName).also { typeBuilder ->

                    typeBuilder.superclass(typeStructureType.parameterizedBy(dtoClassName))

                    val fields = fieldDescriptorsByDtoId.getValue(dto.id)

                    typeBuilder.addProperties(fields.map {
                        PropertySpec.builder(
                            name = it.field.name,
                            type = propertyType.importNested().parameterizedBy(it.field.type.kotlinPoet(model))
                        ).delegate(buildCodeBlock { appendPropertyDelegate(it.field, model) })
                            .build()
                    })

                    typeBuilder.addFunction(FunSpec.builder("deserialize").also { funcBuilder ->
                        funcBuilder.addModifiers(OVERRIDE)
                        funcBuilder.addParameter("context", deserializationContextType)
                        funcBuilder.returns(dtoClassName)

                        val codeReferences = mutableListOf<Any>()

                        val createInstance = buildString {
                            when {
                                dto.isObject -> append("%T")
                                fields.isNotEmpty() -> {
                                    append("%T(\n$INDENT")
                                    fields.forEachIndexed { i, field ->
                                        if (i != 0) append(",\n$INDENT")
                                        append("${field.field.name} = this.${field.field.name}.deserialize(context)")
                                    }
                                    append("\n)")
                                }
                                else -> append("%T()")
                            }
                        }

                        val toReturn = if (dto.inheritors.isEmpty() && !dto.hierarchyRole.isAbstract) {
                            codeReferences += dtoClassName
                            createInstance
                        } else {
                            codeReferences += stringTypeType.importNested()
                            "when (val className = %T.deserialize(context.child(\"className\"))) {" +
                                dto.inheritors.joinToString("\n$INDENT", "\n$INDENT", "\n") {
                                    val inheritor = model.resolveDto(it)
                                    val inheritorClassName = inheritor.getClassName()
                                    codeReferences += inheritorClassName.getStructureClassName()
                                    val condition = "\"${inheritor.name}\"" + if (inheritor.inheritors.isNotEmpty()) {
                                        codeReferences += inheritorClassName.getStructureClassName()
                                        ", in %T.childClassNames"
                                    } else ""
                                    "$condition -> %T.deserialize(context)"
                                } +
                                (if (!dto.hierarchyRole.isAbstract) {
                                    codeReferences += dtoClassName
                                    "$INDENT\"${dto.name}\" -> " +
                                        createInstance.indentNonFirst() + "\n"
                                } else "") +
                                "${INDENT}else -> error(\"Unsupported class name: '\$className'\")\n}"
                        }

                        funcBuilder.addCode("return $toReturn", *codeReferences.toTypedArray())
                    }.build())

                    typeBuilder.addFunction(FunSpec.builder("serialize").also { func ->
                        func.addModifiers(OVERRIDE)
                        func.addParameter("value", dtoClassName)
                        func.returns(jsonValueType)

                        val codeReferences = mutableListOf<Any>()

                        val createJson = "%M(listOfNotNull(" + (fields.takeIf { it.isNotEmpty() }
                            ?.joinToString(",\n$INDENT", "\n$INDENT", "\n") {
                                "this.${it.field.name}.serialize(value.${it.field.name})"
                            } ?: "") + "))"

                        val toReturn = if (dto.inheritors.isEmpty() && !dto.hierarchyRole.isAbstract) {
                            codeReferences += jsonObjectFunction
                            createJson
                        } else {
                            "when (value) {" +
                                dto.inheritors.joinToString("\n$INDENT", "\n$INDENT", "\n") {
                                    val inheritor = model.resolveDto(it)
                                    val inheritorClassName = inheritor.getClassName()
                                    codeReferences += inheritorClassName
                                    codeReferences += inheritorClassName.getStructureClassName()
                                    "is %T -> %T.serialize(value).withClassName(\"${inheritor.name}\")"
                                } +
                                "${INDENT}else -> " +
                                if (!dto.hierarchyRole.isAbstract) {
                                    codeReferences += jsonObjectFunction
                                    createJson.indentNonFirst() + ".withClassName(\"${dto.name}\")"
                                } else {
                                    "error(\"Unsupported class\")"
                                } +
                                "\n}"
                        }

                        func.addCode("return $toReturn", *codeReferences.toTypedArray())
                    }.build())

                    if (dto.inheritors.isNotEmpty()) {
                        typeBuilder.addProperty(
                            PropertySpec.builder("childClassNames", SET.parameterizedBy(STRING), OVERRIDE)
                                .initializer(buildCodeBlock {
                                    add("setOf(")
                                    dto.inheritors.forEachIndexed { i, it ->
                                        if (i != 0) add(", ")
                                        add("%S", model.resolveDto(it).name)
                                    }
                                    add(")")
                                    dto.inheritors.forEach {
                                        val inheritor = model.resolveDto(it)
                                        if (inheritor.inheritors.isNotEmpty()) {
                                            add("·+ %T.childClassNames", inheritor.getClassName().getStructureClassName())
                                        }
                                    }
                                })
                                .build()
                        )
                    }
                }.build())
            }
        }.build()
    }
}

private fun String.indentNonFirst() = if ('\n' in this) {
    substringBefore('\n') + '\n' + substringAfter('\n').prependIndent(INDENT)
} else this
