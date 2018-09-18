package kr.pe.ecmaxp.openpie.console

import kr.pe.ecmaxp.micropython.example.MicroPython
import kr.pe.ecmaxp.micropython.example.MicroPython_link.Reset_Handler
import kr.pe.ecmaxp.thumbjk.Interrupt
import kr.pe.ecmaxp.thumbsk.CPU
import kr.pe.ecmaxp.thumbsk.Memory
import kr.pe.ecmaxp.thumbsk.MemoryFlag
import kr.pe.ecmaxp.thumbsk.helper.RegisterIndex
import kr.pe.ecmaxp.thumbsk.signal.ControlPauseSignal
import kr.pe.ecmaxp.thumbsk.signal.ControlStopSignal
import java.io.File
import java.nio.file.Files
import java.time.DateTimeException
import java.time.Instant
import java.util.*
import kotlin.system.exitProcess


object OpenMachineMain {
    internal var pos = 0


    @Throws(Exception::class, ControlPauseSignal::class, ControlStopSignal::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val mp = MicroPython(Memory())
        val memory = mp.memory

        val file = File("C:\\Users\\EcmaXp\\Dropbox\\Projects\\openpie\\oprom\\build\\firmware.bin")
        val firmware = Files.readAllBytes(file.toPath())
        val KB = 1024
        memory.map(0x08000000L, 256 * KB, MemoryFlag.RX) // flash
        memory.map(0x20000000L, 64 * KB, MemoryFlag.RW) // sram

        val mem = HashMap<Long, Int>()
        fun handle (addr: Long, is_read: Boolean, size: Int, value: Int): Int {
            if (!is_read) {
                print("${addr} ${size} ${value}\n")
                mem[addr] = value
            }

            else {
                return when (addr) {
                    0x40000200L -> { // mp_hal_ticks_ms
                        0
                    }
                    else -> {
                        mem[addr]!!
                        // TODO("missing")
                    }
                }
            }

            return 0;
        }

        memory.map(0x40000000L, 4 * KB) { addr: Long, is_read: Boolean, size: Int, value: Int
            -> handle(addr, is_read, size, value)
        }

        // peripheral
        memory.map(0x60000000L, 192 * KB, MemoryFlag.RW) // ram
        memory.map(0xE0000000L, 16 * KB, MemoryFlag.RW) // syscall
        memory.writeBuffer(0x08000000, firmware)

        mp.refCPU.regs[RegisterIndex.PC] = Reset_Handler
        assert(memory.readInt(0x08000000 + 4) == Reset_Handler)

        /*
        private fun interruptResponseBuffer(buffer: ByteArray) {
            cpu!!.memory.writeInt(bufAddress, bufAddress + 8) // + 0
            cpu!!.memory.writeInt(bufAddress + 4, buffer.size) // + 4
            cpu!!.memory.writeBuffer(bufAddress + 8, buffer) // + 8
            cpu!!.memory.writeByte(bufAddress + 8 + buffer.size, 0.toByte()) // + 8 + n
            cpu!!.regs.set(R0, bufAddress)
        }
         */


        val refMemory = memory.copy()
        var refCPU = CPU()
        refCPU.memory = refMemory
        refCPU.regs[RegisterIndex.PC] = Reset_Handler
        refCPU.interruptHandler = {
            throw Exception(it.toString())
        }

        val start2 = Instant.now()
        try{
            while (true)
                refCPU.run(10000)
        } catch (e: Exception) {
            // throw e;
            println("done $e")
            println(Instant.now().toEpochMilli() - start2.toEpochMilli())
        }

        refCPU = CPU()
        refCPU.memory = refMemory
        refCPU.regs[RegisterIndex.PC] = Reset_Handler
        refCPU.interruptHandler = {
            throw Exception(it.toString())
        }

        val start3 = Instant.now()
        try{
            while (true)
                refCPU.run(10000)
        } catch (e: Exception) {
            // throw e;
            println("done $e")
            println(Instant.now().toEpochMilli() - start3.toEpochMilli())
        }

        val newMemory = memory.copy()
        val newMp = MicroPython(newMemory)
        newMp.refCPU.regs[RegisterIndex.PC] = Reset_Handler

        try {
            mp.Reset_Handler {
                svc ->
                run {
                    println(svc)
                    throw Exception()
                }
            }
        } catch (e: Exception){
        }

        val start = Instant.now()
        newMp.Reset_Handler {
            svc ->
            run {
                println(svc)
                println(Instant.now().toEpochMilli() - start.toEpochMilli())
                exitProcess(0);
            }
        }

    }
}
