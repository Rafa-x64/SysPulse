<div align="center">

# SysPulse

**A real-time Linux system monitor built with Java 25, JavaFX 23 and a native C backend**

![SysPulse Dashboard](preview.png)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![JavaFX](https://img.shields.io/badge/JavaFX-23.0.1-purple.svg)](https://openjfx.io/)
[![Platform](https://img.shields.io/badge/Platform-Linux-lightgrey.svg)](#)
[![Version](https://img.shields.io/badge/Version-1.0-brightgreen.svg)](https://github.com/Rafa-x64/SysPulse/releases/tag/v1.0)

</div>

---

## What is SysPulse?

SysPulse is a lightweight, native-feeling desktop dashboard for Linux that gives you a live pulse on your machine. It reads system data directly from the Linux kernel (`/proc`, `/sys`, disk stats) through a native C shared library and surfaces everything in a clean JavaFX interface with real-time animated charts.

No bloat. No Electron. No background daemons. Just raw kernel data, rendered at 60 fps.

### What it monitors

| Metric | Source | Update rate |
|--------|--------|-------------|
| CPU usage | `/proc/stat` delta | 1 s |
| RAM usage | `/proc/meminfo` | 1 s |
| Disk I/O activity | `/proc/diskstats` delta | 1 s |
| GPU usage | `/sys/class/drm/...` | 1 s |
| Running processes | `/proc/<pid>/stat` | 1 s |

### Process management

Select any process in the table and use the bottom toolbar to:

- **Info** - view PID, PPID, memory and thread count
- **Terminate** - send SIGTERM (graceful shutdown)
- **Kill -9** - send SIGKILL (force kill)
- **Suspend** - send SIGSTOP (pause process)
- **Resume** - send SIGCONT (resume process)

---

## Requirements

| Dependency | Version | Link |
|------------|---------|------|
| Java (JDK) | 25 | [openjdk.org](https://openjdk.org/projects/jdk/25/) |
| Apache Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| GCC | Any modern version | [gcc.gnu.org](https://gcc.gnu.org/) |
| Linux kernel | 5.x+ | - |
| JavaFX | 23.0.1 (pulled by Maven) | [openjfx.io](https://openjfx.io/) |

> **GPU monitoring** works out of the box on AMD cards via `gpu_busy_percent` and on Intel integrated graphics via frequency-based estimation. NVIDIA is not currently supported.

---

## Getting started

### 1. Clone the repository

```bash
git clone https://github.com/Rafa-x64/SysPulse.git
cd SysPulse
```

### 2. Build and run

Maven will automatically compile the C native library (`libsysmetrics.so`) before compiling the Java sources.

```bash
mvn javafx:run
```

That is the only command you need. Maven handles everything:
- Compiles `src/main/c/sys_metrics.c` into `lib/libsysmetrics.so`
- Downloads JavaFX from Maven Central
- Compiles the Java sources
- Launches the application

---

## Project structure

```
SysPulse/
├── src/
│   └── main/
│       ├── c/
│       │   └── sys_metrics.c          # Native library: reads /proc and /sys
│       ├── java/com/rafa/
│       │   ├── App.java               # JavaFX entry point
│       │   ├── bridge/
│       │   │   └── SystemBridge.java  # FFI bridge (Panama API)
│       │   ├── model/
│       │   │   ├── SystemMetrics.java # Immutable metrics snapshot
│       │   │   ├── ProcessSnapshot.java
│       │   │   └── RawCpuTimes.java
│       │   ├── service/
│       │   │   └── SystemMonitorService.java  # Polling scheduler
│       │   ├── view/
│       │   │   ├── DashboardView.java         # UI layout and wiring
│       │   │   └── CpuChartCanvas.java        # Animated line chart canvas
│       │   └── viewmodel/
│       │       └── DashboardViewModel.java    # Observable properties
│       └── resources/com/rafa/
│           └── styles.css             # Nord-themed dark stylesheet
├── lib/                               # Compiled .so goes here (git-ignored)
├── pom.xml
├── LICENSE
└── README.md
```

---

## Architecture

SysPulse uses the [Java Foreign Function & Memory API](https://openjdk.org/jeps/454) (Project Panama) introduced in Java 22 to call the native C library without JNI boilerplate. The data flow is:

```
Linux kernel (/proc, /sys)
        |
  sys_metrics.c  (shared library compiled by GCC)
        |
  SystemBridge.java  (Panama FFI downcall handles)
        |
  SystemMonitorService.java  (virtual-thread scheduler, 1 s interval)
        |
  DashboardViewModel.java  (JavaFX ObservableProperties)
        |
  DashboardView.java  (UI, animated charts, process table)
```

The scheduler runs on a [virtual thread](https://openjdk.org/jeps/444), keeping the platform thread pool free.

---

## Contributing

Pull requests are welcome. To propose a change:

1. Fork the repository
2. Create a branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a pull request against `main`

For larger changes, open an issue first to discuss the approach.

---

## License

Released under the [MIT License](LICENSE). Do whatever you want with it.
