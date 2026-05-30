package com.rafa.bridge;

import com.rafa.model.ProcessSnapshot;
import com.rafa.model.RawCpuTimes;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;

public final class SystemBridge {

    private static final StructLayout CPU_LAYOUT = MemoryLayout.structLayout(
        JAVA_LONG.withName("user"),    JAVA_LONG.withName("nice"),
        JAVA_LONG.withName("system"),  JAVA_LONG.withName("idle"),
        JAVA_LONG.withName("iowait"),  JAVA_LONG.withName("irq"),
        JAVA_LONG.withName("softirq")
    );

    private static final StructLayout MEM_LAYOUT = MemoryLayout.structLayout(
        JAVA_LONG.withName("total"), JAVA_LONG.withName("free"), JAVA_LONG.withName("available")
    );

    private static final StructLayout DISK_LAYOUT = MemoryLayout.structLayout(
        JAVA_LONG.withName("total_bytes"), JAVA_LONG.withName("free_bytes")
    );

    private static final StructLayout PROC_LAYOUT = MemoryLayout.structLayout(
        JAVA_INT.withName("pid"),
        JAVA_INT.withName("ppid"),
        JAVA_INT.withName("threads"),
        JAVA_INT.withName("priority"),
        MemoryLayout.sequenceLayout(4, JAVA_BYTE).withName("state"),
        MemoryLayout.sequenceLayout(32, JAVA_BYTE).withName("name"),
        JAVA_INT.withName("padding"),
        JAVA_LONG.withName("mem_kb")
    );

    private static final MethodHandle READ_CPU;
    private static final MethodHandle READ_MEM;
    private static final MethodHandle READ_DISK;
    private static final MethodHandle READ_DISK_IO;
    private static final MethodHandle READ_GPU;
    private static final MethodHandle GET_PROCS;

    static {
        System.loadLibrary("sysmetrics");
        var linker = Linker.nativeLinker();
        var lookup = SymbolLookup.loaderLookup();
        
        READ_CPU = linker.downcallHandle(lookup.find("read_cpu_times").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
        READ_MEM = linker.downcallHandle(lookup.find("read_mem_info").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
        READ_DISK = linker.downcallHandle(lookup.find("read_disk_info").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
        READ_DISK_IO = linker.downcallHandle(lookup.find("read_disk_io_ticks").orElseThrow(), FunctionDescriptor.of(JAVA_LONG));
        READ_GPU = linker.downcallHandle(lookup.find("read_gpu_usage").orElseThrow(), FunctionDescriptor.of(JAVA_DOUBLE));
        GET_PROCS = linker.downcallHandle(lookup.find("get_processes").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    }

    public static RawCpuTimes sampleCpuTimes() {
        try (var arena = Arena.ofConfined()) {
            var seg = arena.allocate(CPU_LAYOUT);
            READ_CPU.invokeExact(seg);
            return new RawCpuTimes(
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("user"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("nice"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("system"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("idle"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("iowait"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("irq"))),
                seg.get(JAVA_LONG, CPU_LAYOUT.byteOffset(groupElement("softirq")))
            );
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static long[] sampleMemInfo() {
        try (var arena = Arena.ofConfined()) {
            var seg = arena.allocate(MEM_LAYOUT);
            READ_MEM.invokeExact(seg);
            return new long[]{
                seg.get(JAVA_LONG, MEM_LAYOUT.byteOffset(groupElement("total"))),
                seg.get(JAVA_LONG, MEM_LAYOUT.byteOffset(groupElement("available")))
            };
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static long[] sampleDiskInfo() {
        try (var arena = Arena.ofConfined()) {
            var seg = arena.allocate(DISK_LAYOUT);
            READ_DISK.invokeExact(seg);
            return new long[]{
                seg.get(JAVA_LONG, DISK_LAYOUT.byteOffset(groupElement("total_bytes"))),
                seg.get(JAVA_LONG, DISK_LAYOUT.byteOffset(groupElement("free_bytes")))
            };
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static long sampleDiskIoTicks() {
        try { return (long) READ_DISK_IO.invokeExact(); }
        catch (Throwable t) { return 0L; }
    }

    public static double sampleGpuUsage() {
        try { return (double) READ_GPU.invokeExact(); } 
        catch (Throwable t) { return 0.0; }
    }

    public static List<ProcessSnapshot> getProcesses() {
        int maxProcs = 500;
        try (var arena = Arena.ofConfined()) {
            var seg = arena.allocate(PROC_LAYOUT, maxProcs);
            int count = (int) GET_PROCS.invokeExact(seg, maxProcs);
            List<ProcessSnapshot> list = new ArrayList<>(count);
            
            for (int i = 0; i < count; i++) {
                var elem = seg.asSlice(i * PROC_LAYOUT.byteSize(), PROC_LAYOUT.byteSize());
                int pid = elem.get(JAVA_INT, PROC_LAYOUT.byteOffset(groupElement("pid")));
                int ppid = elem.get(JAVA_INT, PROC_LAYOUT.byteOffset(groupElement("ppid")));
                int threads = elem.get(JAVA_INT, PROC_LAYOUT.byteOffset(groupElement("threads")));
                int priority = elem.get(JAVA_INT, PROC_LAYOUT.byteOffset(groupElement("priority")));
                long mem = elem.get(JAVA_LONG, PROC_LAYOUT.byteOffset(groupElement("mem_kb")));
                
                String state = elem.asSlice(PROC_LAYOUT.byteOffset(groupElement("state")), 4).getString(0);
                String name = elem.asSlice(PROC_LAYOUT.byteOffset(groupElement("name")), 32).getString(0);
                
                list.add(new ProcessSnapshot(pid, ppid, threads, priority, state, name, mem));
            }
            return list;
        } catch (Throwable t) { throw new RuntimeException(t); }
    }
}
