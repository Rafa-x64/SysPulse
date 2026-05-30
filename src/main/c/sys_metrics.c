#include <stdio.h>
#include <string.h>
#include <sys/statvfs.h>
#include <dirent.h>
#include <stdlib.h>
#include <ctype.h>

// --- CPU ---
typedef struct {
    long user, nice, system, idle, iowait, irq, softirq;
} CpuTimes;

void read_cpu_times(CpuTimes *out) {
    FILE *f = fopen("/proc/stat", "r");
    if (!f) return;
    fscanf(f, "cpu %ld %ld %ld %ld %ld %ld %ld",
           &out->user, &out->nice, &out->system, &out->idle,
           &out->iowait, &out->irq, &out->softirq);
    fclose(f);
}

// --- RAM ---
typedef struct {
    long total, free, available;
} MemInfo;

void read_mem_info(MemInfo *out) {
    FILE *f = fopen("/proc/meminfo", "r");
    if (!f) return;
    char buf[128];
    while (fgets(buf, sizeof(buf), f)) {
        if (strncmp(buf, "MemTotal:", 9) == 0) sscanf(buf, "MemTotal: %ld kB", &out->total);
        else if (strncmp(buf, "MemFree:", 8) == 0) sscanf(buf, "MemFree: %ld kB", &out->free);
        else if (strncmp(buf, "MemAvailable:", 13) == 0) sscanf(buf, "MemAvailable: %ld kB", &out->available);
    }
    fclose(f);
}

// --- DISK ---
typedef struct {
    long total_bytes, free_bytes;
} DiskInfo;

void read_disk_info(DiskInfo *out) {
    struct statvfs stat;
    if (statvfs("/", &stat) == 0) {
        out->total_bytes = stat.f_blocks * stat.f_frsize;
        out->free_bytes = stat.f_bavail * stat.f_frsize;
    } else {
        out->total_bytes = 0;
        out->free_bytes = 0;
    }
}

// --- DISK IO ---
long read_disk_io_ticks() {
    FILE *f = fopen("/proc/diskstats", "r");
    if (!f) return 0;
    
    char buf[256];
    long max_ticks = 0;
    while (fgets(buf, sizeof(buf), f)) {
        int major, minor;
        char name[32];
        long f0, f1, f2, f3, f4, f5, f6, f7, f8, io_ticks;
        
        if (sscanf(buf, "%d %d %31s %ld %ld %ld %ld %ld %ld %ld %ld %ld %ld",
                   &major, &minor, name, 
                   &f0, &f1, &f2, &f3, &f4, &f5, &f6, &f7, &f8, &io_ticks) == 13) {
            if (strncmp(name, "loop", 4) == 0 || strncmp(name, "ram", 3) == 0) continue;
            if (io_ticks > max_ticks) max_ticks = io_ticks;
        }
    }
    fclose(f);
    return max_ticks;
}

// --- GPU ---
// Try AMD gpu_busy_percent, then Intel frequency-based estimate.
// Tries card0 through card3. Returns -1.0 if no GPU data is available.
static double try_read_int(const char *path) {
    FILE *f = fopen(path, "r");
    if (!f) return -1.0;
    int v = 0;
    int r = fscanf(f, "%d", &v);
    fclose(f);
    return (r == 1) ? (double)v : -1.0;
}

double read_gpu_usage() {
    char path[128];
    for (int card = 0; card <= 3; card++) {
        // AMD: direct busy percentage
        snprintf(path, sizeof(path),
                 "/sys/class/drm/card%d/device/gpu_busy_percent", card);
        double busy = try_read_int(path);
        if (busy >= 0.0) return busy;

        // Intel: frequency-based proxy (act_freq / max_freq * 100)
        snprintf(path, sizeof(path),
                 "/sys/class/drm/card%d/gt_act_freq_mhz", card);
        double act = try_read_int(path);
        if (act >= 0.0) {
            snprintf(path, sizeof(path),
                     "/sys/class/drm/card%d/gt_max_freq_mhz", card);
            double max = try_read_int(path);
            if (max > 0.0) return (act / max) * 100.0;
        }
    }
    return -1.0;  // No GPU monitoring available
}

// --- PROCESSES ---
typedef struct {
    int pid;
    int ppid;
    int threads;
    int priority;
    char state[4];
    char name[32];
    int padding;
    long mem_kb;
} ProcessInfo;

int get_processes(ProcessInfo* buffer, int max_count) {
    DIR *dir = opendir("/proc");
    if (!dir) return 0;
    
    struct dirent *ent;
    int count = 0;
    
    while ((ent = readdir(dir)) != NULL && count < max_count) {
        if (!isdigit(ent->d_name[0])) continue;
        
        int pid = atoi(ent->d_name);
        char path[256];
        snprintf(path, sizeof(path), "/proc/%d/stat", pid);
        
        FILE *f = fopen(path, "r");
        if (f) {
            char comm[32];
            char state;
            long rss = 0;
            int ppid = 0, priority = 0, threads = 0;
            
            // stat format fields: 
            // 1:pid 2:comm 3:state 4:ppid 
            // 5-17: skip 13 fields
            // 18:priority 19:nice 20:threads
            // 21-23: skip 3 fields
            // 24:rss
            fscanf(f, "%*d (%31[^)]) %c %d %*d %*d %*d %*d %*u %*lu %*lu %*lu %*lu %*lu %*lu %*ld %*ld %d %*ld %d %*ld %*llu %*lu %ld", 
                   comm, &state, &ppid, &priority, &threads, &rss);
            fclose(f);
            
            buffer[count].pid = pid;
            buffer[count].ppid = ppid;
            buffer[count].priority = priority;
            buffer[count].threads = threads;
            buffer[count].state[0] = state;
            buffer[count].state[1] = '\0';
            
            strncpy(buffer[count].name, comm, 31);
            buffer[count].name[31] = '\0';
            
            buffer[count].mem_kb = rss * 4; // Asumiendo página de 4KB
            buffer[count].padding = 0;
            
            count++;
        }
    }
    closedir(dir);
    return count;
}
