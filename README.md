<div align="center">

# SysPulse

**Un monitor de sistema en tiempo real para Linux construido con Java 25, JavaFX 23 y un backend nativo en C**

![SysPulse Dashboard](preview.png)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![JavaFX](https://img.shields.io/badge/JavaFX-23.0.1-purple.svg)](https://openjfx.io/)
[![Platform](https://img.shields.io/badge/Platform-Linux-lightgrey.svg)](#)
[![Version](https://img.shields.io/badge/Version-1.0-brightgreen.svg)](https://github.com/Rafa-x64/SysPulse/releases/tag/v1.0)

</div>

---

## ¿Qué es SysPulse?

SysPulse es un panel de escritorio ligero y nativo para Linux que te da un pulso en tiempo real de tu máquina. Lee los datos directamente desde el Kernel de Linux (`/proc`, `/sys`, estadísticas de disco) a través de una librería nativa compartida escrita en C y muestra todo en una interfaz limpia en JavaFX con gráficos animados en tiempo real.

Sin sobrecarga. Sin Electron. Sin daemons en segundo plano. Solo datos puros del kernel, renderizados a 60 fps.

### Qué monitorea

| Métrica | Origen | Frecuencia de actualización |
|:---|:---|:---|
| Uso de CPU | Delta de `/proc/stat` | 1 s |
| Uso de RAM | `/proc/meminfo` | 1 s |
| Actividad de Disco I/O | Delta de `/proc/diskstats` | 1 s |
| Uso de GPU | `/sys/class/drm/...` | 1 s |
| Procesos activos | `/proc/<pid>/stat` | 1 s |

### Administración de procesos

Selecciona cualquier proceso de la tabla y utiliza la barra de herramientas inferior para:

- **Info** - ver el PID, PPID, memoria y cantidad de hilos de ejecución.
- **Terminar** - enviar la señal `SIGTERM` (cierre elegante).
- **Kill -9** - enviar la señal `SIGKILL` (forzar el cierre del proceso).
- **Suspender** - enviar la señal `SIGSTOP` (pausar el proceso).
- **Reanudar** - enviar la señal `SIGCONT` (continuar el proceso pausado).

---

## Requisitos

| Dependencia | Versión | Enlace |
|:---|:---|:---|
| Java (JDK) | 25 | [openjdk.org](https://openjdk.org/projects/jdk/25/) |
| Apache Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| GCC | Cualquier versión moderna | [gcc.gnu.org](https://gcc.gnu.org/) |
| Kernel de Linux | 5.x+ | - |
| JavaFX | 23.0.1 (descargado por Maven) | [openjfx.io](https://openjfx.io/) |

> ℹ️ El **Monitoreo de GPU** funciona de forma nativa en tarjetas gráficas AMD mediante `gpu_busy_percent` y en gráficas integradas Intel mediante una estimación basada en frecuencia. Las tarjetas NVIDIA no están soportadas actualmente.

---

## Cómo usar la aplicación (Quick Start & Run)

### 📌 Opción 1: Ejecutar directamente (Versión portable para usuarios)
Si descargaste la versión precompilada (el archivo `.rar` o `.tar.gz` con el bundle compilado), **no necesitas instalar Java ni tener herramientas de desarrollo**. 

> [!IMPORTANT]
> **Es fundamental mantener toda la estructura de carpetas extraída**. El ejecutable binario depende de las carpetas y archivos internos que se encuentran dentro de `lib/` (incluyendo la máquina virtual embebida y la librería nativa `libsysmetrics.so`).

1. **Extrae el archivo comprimido**:
   ```bash
   # Extrae el archivo en tu sistema (ej. SysPulse.rar o SysPulse.tar.gz)
   unrar x SysPulse.rar
   # o si es un tar.gz:
   tar -xzf SysPulse.tar.gz
   ```
2. **Entra en el directorio**:
   ```bash
   cd SysPulse
   ```
3. **Lanza la aplicación**:
   * **Doble Clic:** Haz doble clic sobre el archivo ejecutable `SysPulse` ubicado dentro de `bin/` usando tu gestor de archivos (Dolphin, Nautilus, etc.).
   * **Por terminal:**
     ```bash
     ./bin/SysPulse
     ```

---

## 🛠️ Desarrollo y Contribución (Para Desarrolladores)

Si deseas modificar el código fuente, agregar nuevas visualizaciones o características, puedes configurar tu entorno y compilarlo desde cero.

### Requisitos de desarrollo
* **Java JDK 25** (Se recomienda GraalVM JDK 25 para soporte nativo completo).
* **Apache Maven 3.9+**.
* **GCC** (Para compilar la librería nativa en C `sys_metrics.c`).

### 1. Clonar el repositorio
```bash
git clone https://github.com/Rafa-x64/SysPulse.git
cd SysPulse
```

### 2. Ejecutar en modo desarrollo
Puedes arrancar la aplicación de inmediato. Maven se encargará de compilar la librería en C (`libsysmetrics.so`), descargar JavaFX, compilar el código Java y lanzar el panel:
```bash
mvn javafx:run
```

### 3. Generar el ejecutable portable (jpackage)
Para generar el paquete redistribuible (el ejecutable con su propio entorno de ejecución embebido) en la carpeta `dist/`, utiliza el script automatizado que empaqueta la aplicación de forma limpia:
```bash
# Dar permisos de ejecución si no los tiene
chmod +x build-exe.sh

# Compilar y empaquetar
./build-exe.sh
```

El bundle autocontenido con todo lo necesario se generará en:
```
dist/SysPulse/
```
Esta es la carpeta completa que puedes comprimir en `.rar` o `.tar.gz` para compartirla con cualquier usuario de Linux x86_64.

---

## Estructura del proyecto

```
SysPulse/
├── src/
│   └── main/
│       ├── c/
│       │   └── sys_metrics.c          # Librería nativa C: lee /proc y /sys
│       ├── java/com/rafa/
│       │   ├── App.java               # Punto de entrada de la aplicación JavaFX
│       │   ├── bridge/
│       │   │   └── SystemBridge.java  # Puente FFI (Panama API)
│       │   ├── model/
│       │   │   ├── SystemMetrics.java # Captura inmutable de métricas
│       │   │   ├── ProcessSnapshot.java
│       │   │   └── RawCpuTimes.java
│       │   ├── service/
│       │   │   └── SystemMonitorService.java  # Planificador y recolector (Virtual Threads)
│       │   ├── view/
│       │   │   ├── DashboardView.java         # Diseño y conexión de la UI
│       │   │   └── CpuChartCanvas.java        # Canvas para gráficos de líneas animados
│       │   └── viewmodel/
│       │       └── DashboardViewModel.java    # Propiedades observables de la UI
│       └── resources/com/rafa/
│           └── styles.css             # Estilo oscuro con temática Nord
├── lib/                               # La librería compilada .so se guarda aquí (git-ignored)
├── pom.xml
├── LICENSE
└── README.md
```

---

## Arquitectura

SysPulse utiliza la [API de Funciones y Memoria Extranjera de Java](https://openjdk.org/jeps/454) (Proyecto Panama) introducida en Java 22 para llamar a la librería nativa escrita en C sin la sobrecarga ni el código boilerplate de JNI. El flujo de datos es:

```
Kernel de Linux (/proc, /sys)
        |
  sys_metrics.c  (librería compartida compilada por GCC)
        |
  SystemBridge.java  (manejadores de llamadas Panama FFI)
        |
  SystemMonitorService.java  (planificador sobre hilos virtuales, intervalo de 1 s)
        |
  DashboardViewModel.java  (propiedades observables de JavaFX)
        |
  DashboardView.java  (UI, gráficos animados, tabla de procesos)
```

El programador se ejecuta sobre un **hilo virtual (Virtual Thread)**, manteniendo libre el pool de hilos de la plataforma.

---

## Contribuciones

Las contribuciones y Pull Requests son completamente bienvenidos. Para proponer un cambio:

1. Realiza un Fork del repositorio.
2. Crea una rama para tu característica (`git checkout -b feature/tu-caracteristica`).
3. Confirma tus cambios (`git commit -m 'Agregar nueva característica'`).
4. Sube los cambios a tu rama (`git push origin feature/tu-caracteristica`).
5. Abre un Pull Request contra la rama `main`.

Para cambios significativos o grandes, por favor abre un Issue primero para discutir tu propuesta.

---

## Licencia

Lanzado bajo la [Licencia MIT](LICENSE). Siéntete libre de hacer lo que desees con el proyecto.
