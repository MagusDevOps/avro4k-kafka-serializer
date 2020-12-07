package com.github.thake.kafka.avro4k.serializer


import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.AvroName
import com.github.avrokotlin.avro4k.AvroNamespace
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.Serializable
import org.apache.avro.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class KafkaAvro4kSerializerTest {
    private val registryMock = spyk(MockSchemaRegistryClient())
    @Serializable
    private data class TestRecord(
        val str : String
    )
    @Serializable
    private data class TestRecordWithNull(
        val nullableStr : String? = null,
        val intValue : Int
    )
    @Serializable
    @AvroNamespace("custom.namespace")
    private data class TestRecordWithNamespace(
        val float : Double
    )
    @Serializable
    @AvroName("AnotherName")
    private data class TestRecordWithDifferentName(
        val double : Double
    )

    companion object{
        @JvmStatic
        fun createSerializableObjects(): Stream<out Any> {
            return Stream.of(
                TestRecord("STTR"),
                TestRecordWithNull(null, 2),
                TestRecordWithNull("33", 1),
                TestRecordWithNamespace(4.0),
                TestRecordWithDifferentName(2.0)
            )
        }
    }

    @Test
    fun testRecordSerializedWithNull() {
        val serializer = KafkaAvro4kSerializer(registryMock)
        serializer.configure(
            mapOf(
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to "true",
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            ),
            false
        )
        val avroSchema = Avro.default.schema(TestRecord.serializer())
        val unionSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), avroSchema)
        val topic = "My-Topic"
        val subjectName = "$topic-value"
        registryMock.register(subjectName, AvroSchema(unionSchema))
        val result = serializer.serialize(topic, null)
        result.shouldBe(null)
    }

    @ParameterizedTest()
    @MethodSource("createSerializableObjects")
    fun testRecordSerDeRoundtrip(toSerialize: Any?) {
        val serializer = KafkaAvro4kSerializer(registryMock)
        serializer.configure(
            mapOf(
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to "true",
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            ),
            false
        )
        val topic = "My-Topic"
        val result = serializer.serialize(topic, toSerialize)
        assertNotNull(result)
        result ?: throw Exception("")
        verify {
            registryMock.register(any(), any<ParsedSchema>())
        }
        verify(inverse = true) {
            registryMock.getId("$topic-value", any<ParsedSchema>())
        }


        val deserializer = KafkaAvro4kDeserializer(
            registryMock,
            mapOf(
                KafkaAvro4kDeserializerConfig.RECORD_PACKAGES to this::class.java.`package`.name,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            )
        )

        val deserializedValue = deserializer.deserialize(topic, result)
        assertEquals(toSerialize, deserializedValue)
    }
}