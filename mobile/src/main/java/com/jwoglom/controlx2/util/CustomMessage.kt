package com.jwoglom.controlx2.util

import androidx.annotation.Keep
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.MessageType
import com.jwoglom.pumpx2.pump.messages.Messages
import com.jwoglom.pumpx2.pump.messages.annotations.MessageProps
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.models.SupportedDevices
import java.io.Serializable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

public fun createMessageWithOpCode(opCode: Byte, characteristic: Characteristic, cargo: ByteArray): Message {
    // First, create the response message class (opCode should be odd for responses)
    // According to pumpX2, request opCodes are even, response opCodes are odd
    val responseOpCode = (opCode.toInt() + 1).toByte()

    if (Messages.OPCODES.containsKey(
            org.apache.commons.lang3.tuple.Pair.of(characteristic, opCode.toInt())
    ) && Messages.OPCODES.containsKey(
            org.apache.commons.lang3.tuple.Pair.of(characteristic, responseOpCode.toInt())
    )) {
        val clazz = Messages.OPCODES.get<Serializable, Class<out Message>>(
            org.apache.commons.lang3.tuple.Pair.of(characteristic, opCode.toInt())
        )
        val msg = clazz!!.newInstance() as Message
        msg.parse(cargo)
        return msg
    }


    // Lazy references to break circular dependencies
    lateinit var responseMessageProps: MessageProps
    lateinit var customMessageProps: MessageProps
    lateinit var requestClassRef: Class<out Message>
    lateinit var responseClassRef: Class<out Message>



    // Create test response message
    class testResponseClass : Message {
        @Keep
        constructor() : super() {
        }

        override fun opCode(): Byte = responseOpCode
        override fun type(): MessageType = MessageType.RESPONSE
        override fun getCharacteristic(): Characteristic = characteristic
        override fun getCargo(): ByteArray = cargo
        override fun signed(): Boolean = false
        override fun stream(): Boolean = false

        override fun parse(raw: ByteArray) {
            // Empty implementation - just accept any data
            this.cargo = if (raw.isEmpty()) EMPTY else raw
        }

        override fun getRequestClass(): Class<out Message> {
            return requestClassRef
        }

        override fun props(): MessageProps? = responseMessageProps
    }

    // Create test request message
    class testRequestClass : Message {
        @Keep
        constructor() : super() {
        }

        override fun opCode(): Byte = opCode
        override fun type(): MessageType = MessageType.REQUEST
        override fun getCharacteristic(): Characteristic = characteristic
        override fun getCargo(): ByteArray = cargo
        override fun signed(): Boolean = false
        override fun stream(): Boolean = false

        override fun parse(raw: ByteArray) {
            // Empty implementation - just accept any data
            this.cargo = if (raw.isEmpty()) EMPTY else raw
        }

        override fun getResponseClass(): Class<out Message> {
            return responseClassRef
        }

        override fun getResponseOpCode(): Byte {
            return responseOpCode
        }

        override fun getRequestProps(): MessageProps? {
            return customMessageProps
        }

        override fun props(): MessageProps? = customMessageProps
    }

    // Initialize class references after both classes are defined
    @Suppress("UNCHECKED_CAST")
    requestClassRef = testRequestClass::class.java as Class<out Message>
    @Suppress("UNCHECKED_CAST")
    responseClassRef = testResponseClass::class.java as Class<out Message>

    // Create MessageProps for response using Proxy
    responseMessageProps = Proxy.newProxyInstance(
        MessageProps::class.java.classLoader,
        arrayOf(MessageProps::class.java),
        InvocationHandler { _, method, _ ->
            when (method.name) {
                "opCode" -> responseOpCode
                "size" -> cargo.size
                "variableSize" -> true
                "stream" -> false
                "signed" -> false
                "type" -> MessageType.RESPONSE
                "response" -> responseClassRef
                "request" -> requestClassRef
                "minApi" -> KnownApiVersion.API_V2_1
                "supportedDevices" -> SupportedDevices.ALL
                "characteristic" -> characteristic
                "modifiesInsulinDelivery" -> false
                "annotationType" -> MessageProps::class.java
                "toString" -> "@${MessageProps::class.java.name}(opCode=$responseOpCode, type=RESPONSE)"
                "hashCode" -> responseOpCode.toInt()
                "equals" -> false
                else -> null
            }
        }
    ) as MessageProps

    // build 'props' as implementation of MessageProps using Proxy
    customMessageProps = Proxy.newProxyInstance(
        MessageProps::class.java.classLoader,
        arrayOf(MessageProps::class.java),
        InvocationHandler { _, method, _ ->
            when (method.name) {
                "opCode" -> opCode
                "size" -> cargo.size
                "variableSize" -> true
                "stream" -> false
                "signed" -> false
                "type" -> MessageType.REQUEST
                "response" -> responseClassRef
                "request" -> requestClassRef
                "minApi" -> KnownApiVersion.API_V2_1
                "supportedDevices" -> SupportedDevices.ALL
                "characteristic" -> characteristic
                "modifiesInsulinDelivery" -> false
                "annotationType" -> MessageProps::class.java
                "toString" -> "@${MessageProps::class.java.name}(opCode=$opCode, type=REQUEST)"
                "hashCode" -> opCode.toInt()
                "equals" -> false
                else -> null
            }
        }
    ) as MessageProps

    Messages.OPCODES[org.apache.commons.lang3.tuple.Pair.of(characteristic, opCode.toInt())] =
        requestClassRef

    Messages.OPCODES[org.apache.commons.lang3.tuple.Pair.of(characteristic, responseOpCode.toInt())] =
        responseClassRef


    return testRequestClass()
}
