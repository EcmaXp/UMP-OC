package kr.pe.ecmaxp.thumbsf

import kr.pe.ecmaxp.thumbsf.exc.InvalidMemoryException
import kr.pe.ecmaxp.thumbsf.exc.UnexceptedLogicError
import kr.pe.ecmaxp.thumbsf.exc.UnknownInstructionException
import kr.pe.ecmaxp.thumbsf.exc.UnsupportedInstructionException
import kr.pe.ecmaxp.thumbsf.helper.*
import java.util.*

class Memory {
    private val _list = ArrayList<MemoryRegion>()

    // cached
    private var _execPage: MemoryRegion? = null
    private var _readPage: MemoryRegion? = null
    private var _writePage: MemoryRegion? = null
    private var _execCache: IntArray? = null

    fun copy(): Memory {
        val memory = Memory()
        for (region in _list) {
            if (region.flag == MemoryFlag.HOOK) {
                memory.map(region)
            } else {
                val newRegion = MemoryRegion(region.begin, region.size, region.flag)
                for (i in 0 until region.buffer.size)
                    newRegion.buffer[i] = region.buffer[i]

                memory.map(newRegion)
            }
        }

        return memory;
    }

    internal fun map(region: MemoryRegion) {
        if (region.size % 4 != 0)
            throw Exception("invalid memory size")

        _list.add(region)

        if (region.flag == MemoryFlag.RX) {
            if (_execPage != null) {
                throw InvalidMemoryException(region.begin)
            }

            _execPage = region
        }
    }

    @Throws(InvalidMemoryException::class)
    fun flash(address: Long, size: Int, firmware: ByteArray) {
        map(address, size, MemoryFlag.RX)
        writeBuffer(address.toInt(), firmware)
        loadExecCache()
    }

    @Throws(InvalidMemoryException::class)
    fun map(address: Long, size: Int, hook: (address: Long, read: Boolean, size: Int, value: Int) -> Int) {
        map(MemoryRegion(address, size, hook))
    }

    @Throws(InvalidMemoryException::class)
    fun map(address: Long, size: Int, flag: MemoryFlag) {
        map(MemoryRegion(address, size, flag))
    }

    @Throws(InvalidMemoryException::class)
    fun readBuffer(address: Int, size: Int): ByteArray {
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_readPage, addr, size)
        _readPage = page

        if (page.flag != MemoryFlag.RX && page.flag != MemoryFlag.RW)
            throw InvalidMemoryException(addr)

        val buffer = ByteArray(size)
        for (i in 0 until size) {
            buffer[i] = readByte(address + i)
        }

        return buffer
    }

    @Throws(InvalidMemoryException::class)
    fun writeBuffer(address: Int, buffer: ByteArray) {
        val size = buffer.size
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_writePage, addr, size)
        _writePage = page

        if (page.flag != MemoryFlag.RX && page.flag != MemoryFlag.RW)
            throw InvalidMemoryException(addr)

        for (i in 0 until size) {
            writeByte(address + i, buffer[i])
        }

        // legacyMemory.writeBuffer(address, buffer)
        // val legacyBuf = legacyMemory.readBuffer(address, size)
        // if (!legacyBuf.contentEquals(buffer)) throw Exception()

        // System.arraycopy(buffer, 0, page.buffer, (addr - page.begin).toInt(), size)
    }

    @Throws(InvalidMemoryException::class)
    fun fetchCode(address: Int): Int {
        val size = 2
        val addr = Integer.toUnsignedLong(address)
        _execPage = updateCache(_execPage, addr, size)
        val page = _execPage!!

        val pos = page.loadKey(addr)
        val bufferCode = page.buffer
        val mvalue = bufferCode[pos]
        val rvalue = when (addr % 4) {
            0L -> mvalue and 0xFFFF
            2L -> (mvalue shr 16) and 0xFFFF
            else -> throw Exception("not align")
        }

        // if (legacyMemory.fetchCode(address) != rvalue) throw Exception()
        return rvalue
    }

    @Throws(InvalidMemoryException::class)
    fun readInt(address: Int): Int {
        val size = 4
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_readPage, addr, size)
        _readPage = page

        val rvalue = when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size)
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                val mvalue = buffer[pos]
                when (addr % 4) {
                    0L ->
                        mvalue
                    else -> throw Exception("not align")
                }
            }
        }

        // if (legacyMemory.readInt(address) != rvalue) throw Exception()
        return rvalue
    }

    @Throws(InvalidMemoryException::class)
    fun readShort(address: Int): Short {
        val size = 2
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_readPage, addr, size)
        _readPage = page

        /*
                return (buffer[pos++].toInt() and 0xFF) or
                (buffer[pos++].toInt() and 0xFF shl 8) or
                (buffer[pos++].toInt() and 0xFF shl 16) or
                (buffer[pos].toInt() and 0xFF shl 24)
         */
        val rvalue = when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size).toShort()
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                val mvalue = buffer[pos]
                when (addr % 4) {
                    0L ->
                        mvalue.toShort()
                    2L ->
                        (mvalue shr 16).toShort()
                    else -> throw Exception("not align")
                }
            }
        }

        // if (legacyMemory.readShort(address) != rvalue) throw Exception()
        return rvalue
    }

    @Throws(InvalidMemoryException::class)
    fun readByte(address: Int): Byte {
        val size = 1
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_readPage, addr, size)
        _readPage = page

        val rvalue = when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size).toByte()
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                val mvalue = buffer[pos]
                when (addr % 4) {
                    0L ->
                        mvalue.toByte()
                    1L ->
                        (mvalue shr 8).toByte()
                    2L ->
                        (mvalue shr 16).toByte()
                    3L ->
                        (mvalue shr 24).toByte()
                    else -> throw Exception("not align")
                }
            }
        }

        return rvalue
    }

    @Throws(InvalidMemoryException::class)
    fun writeInt(address: Int, value: Int) {
        val size = 4
        val addr = Integer.toUnsignedLong(address)
        val page = updateCache(_writePage, addr, size)
        _writePage = page

        // legacyMemory.writeInt(address, value)
        when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size, value)
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                buffer[pos] = when (addr % 4) {
                    0L ->
                        value
                    else -> throw Exception("not align")
                }
            }
        }
    }

    @Throws(InvalidMemoryException::class)
    fun writeShort(address: Int, shortValue: Short) {
        val size = 2
        val addr = Integer.toUnsignedLong(address)
        val value = shortValue.toInt() and 0xFFFF
        val page = updateCache(_writePage, addr, size)
        _writePage = page

        when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size, value)
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                val mvalue = buffer[pos]
                buffer[pos] = when (addr % 4) {
                    0L ->
                        (mvalue and 0xFFFF.inv()) or value
                    2L ->
                        (mvalue and 0xFFFF) or (value shl 16)
                    else -> throw Exception("not align")
                }
            }
        }
    }

    @Throws(InvalidMemoryException::class)
    fun writeByte(address: Int, byteValue: Byte) {
        val size = 1
        val addr = Integer.toUnsignedLong(address)
        val value = byteValue.toInt() and 0xFF
        val page = updateCache(_writePage, addr, size)
        _writePage = page

        when (page.flag) {
            MemoryFlag.HOOK -> {
                page.hook(addr, size, value)
            }
            else -> {
                val pos = page.loadKey(addr)
                val buffer = page.buffer
                val mvalue = buffer[pos]
                buffer[pos] = when (addr % 4) {
                    0L ->
                        (mvalue and 0x000000FF.inv()) or value
                    1L ->
                        (mvalue and 0x0000FF00.inv()) or (value shl 8)
                    2L ->
                        (mvalue and 0x00FF0000.inv()) or (value shl 16)
                    3L ->
                        (mvalue and 0x00FFFFFF) or (value shl 24)
                    else -> throw Exception("not align")
                }
            }
        }
    }

    @Throws(InvalidMemoryException::class)
    private fun updateCache(region: MemoryRegion?, address: Long, size: Int): MemoryRegion {
        return if (region != null && region.begin <= address && address + size <= region.end) {
            region
        } else {
            findRegion(address, size)
        }

    }

    @Throws(InvalidMemoryException::class)
    fun findRegion(address: Long, size: Int): MemoryRegion {
        for (page in _list) {
            if (!(page.begin <= address && address + size <= page.end)) continue
            return page
        }

        throw InvalidMemoryException(address)
    }

    fun loadExecCache(): Pair<IntArray, Int> {
        val region = _execPage!!
        val base = region.begin.toInt()
        if (_execCache == null) {
            _execCache = IntArray(region.size)

            val buffer = _execCache!!
            for (addr in region.begin.toInt() until region.end.toInt() step 2) {
                var code: Int = ERROR
                var imm32: Int = 0

                try {
                    val (first, second) = decode(this, addr)
                    code = first
                    imm32 = second
                } catch (e: UnknownInstructionException) {
                    code = ERROR
                } catch (e: UnsupportedInstructionException) {
                    code = ERROR
                }

                when (code and 0xFF) {
                    NULL -> throw UnexceptedLogicError()
                }

                buffer[addr - base] = code
                buffer[addr - base + 1] = imm32
            }
        }

        return Pair(_execCache!!, base)
    }
}