#!/usr/bin/env bash
set -e

# ─── Configuración ───────────────────────────────────────────────────────────
JAVA_HOME_DIR="$(dirname $(dirname $(readlink -f $(which java))))"

M2_REPO="$HOME/.m2/repository"
JAVAFX_VERSION="23.0.1"
JAVAFX_DIR="$M2_REPO/org/openjfx"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
DIST_DIR="$PROJECT_DIR/dist"
APP_NAME="SysPulse"
MAIN_MODULE="com.rafa"
MAIN_CLASS="com.rafa.App"
LIB_DIR="$PROJECT_DIR/lib"

echo "================================================"
echo "  SysPulse — Generando ejecutable nativo"
echo "================================================"
echo ""

# ─── 1. Compilar con Maven ────────────────────────────────────────────────────
echo "[1/5] Compilando proyecto con Maven..."
mvn clean package -DskipTests -q
echo "      ✓ JAR generado: target/syspulse-1.0.jar"
echo ""

# ─── 2. Preparar módulos JavaFX ──────────────────────────────────────────────
echo "[2/5] Preparando módulos JavaFX..."
JMODS_DIR="$TARGET_DIR/jmods-staging"
mkdir -p "$JMODS_DIR"

for mod in javafx-base javafx-controls javafx-graphics; do
    jar_linux="$JAVAFX_DIR/$mod/$JAVAFX_VERSION/$mod-$JAVAFX_VERSION-linux.jar"
    jar_base="$JAVAFX_DIR/$mod/$JAVAFX_VERSION/$mod-$JAVAFX_VERSION.jar"
    if [ -f "$jar_linux" ]; then
        cp "$jar_linux" "$JMODS_DIR/"
    elif [ -f "$jar_base" ]; then
        cp "$jar_base" "$JMODS_DIR/"
    else
        echo "ERROR: No se encontró $mod JAR en $JAVAFX_DIR"
        exit 1
    fi
done
# También copiar el JAR de la app al staging (necesario para que jlink resuelva com.rafa)
cp "$TARGET_DIR/syspulse-1.0.jar" "$JMODS_DIR/"
echo "      ✓ JARs preparados"
echo ""

# ─── 3. Construir runtime con jlink ──────────────────────────────────────────
echo "[3/5] Construyendo runtime mínimo con jlink..."
RUNTIME_DIR="$TARGET_DIR/runtime"
rm -rf "$RUNTIME_DIR"

# jlink necesita el module-path con: jmods del JDK + JARs de JavaFX + JAR de la app
JLINK_MODULE_PATH="$JAVA_HOME_DIR/jmods:$JMODS_DIR"

jlink \
    --module-path "$JLINK_MODULE_PATH" \
    --add-modules "$MAIN_MODULE,javafx.controls,javafx.graphics,javafx.base,java.base,java.logging,java.management,java.xml,jdk.unsupported" \
    --output "$RUNTIME_DIR" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6

echo "      ✓ Runtime creado ($(du -sh "$RUNTIME_DIR" | cut -f1))"
echo ""

# ─── 4. Crear imagen con jpackage ─────────────────────────────────────────────
echo "[4/5] Empaquetando con jpackage..."
rm -rf "$DIST_DIR"

# Crear un directorio limpio de input (solo el JAR + la librería nativa).
# NO usar target/ directamente porque jpackage mete TODO lo que encuentra
# (jmods-staging, runtime, etc.) en el classpath, lo que genera rutas rotas.
JPACKAGE_INPUT="$TARGET_DIR/jpackage-input"
rm -rf "$JPACKAGE_INPUT"
mkdir -p "$JPACKAGE_INPUT"
cp "$TARGET_DIR/syspulse-1.0.jar" "$JPACKAGE_INPUT/"
[ -f "$LIB_DIR/libsysmetrics.so" ] && cp "$LIB_DIR/libsysmetrics.so" "$JPACKAGE_INPUT/"

# Nota: cuando se usa --runtime-image NO se pasa --module-path ni --add-modules.
# Los módulos JavaFX ya están embebidos en el runtime creado por jlink.
# La librería nativa va en --input, así que $APPDIR apunta exactamente a ella.
jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version "1.0" \
    --input "$JPACKAGE_INPUT" \
    --main-jar "syspulse-1.0.jar" \
    --main-class "$MAIN_CLASS" \
    --runtime-image "$RUNTIME_DIR" \
    --dest "$DIST_DIR" \
    --java-options "--enable-native-access=$MAIN_MODULE" \
    --java-options "-Djava.library.path=\$APPDIR"

echo "      ✓ Imagen creada en: dist/$APP_NAME"
echo ""

# ─── 5. Verificar librería nativa en el bundle ───────────────────────────────
echo "[5/5] Verificando librería nativa..."
SO_PATH="$DIST_DIR/$APP_NAME/lib/app/libsysmetrics.so"
if [ -f "$SO_PATH" ]; then
    echo "      ✓ libsysmetrics.so incluida en el bundle (lib/app/)"
else
    echo "      ⚠  libsysmetrics.so no encontrada en el bundle"
fi
echo ""

# ─── Resultado ────────────────────────────────────────────────────────────────
EXEC_PATH="$DIST_DIR/$APP_NAME/bin/$APP_NAME"
chmod +x "$EXEC_PATH" 2>/dev/null || true

echo "================================================"
echo "  ✅ ¡Ejecutable listo!"
echo ""
echo "  Ubicación: dist/$APP_NAME/bin/$APP_NAME"
echo ""
echo "  Para lanzarlo desde terminal:"
echo "    ./dist/$APP_NAME/bin/$APP_NAME"
echo "================================================"
