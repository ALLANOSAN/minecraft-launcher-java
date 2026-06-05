#!/bin/bash
# MineLauncher - Executar Launcher

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/minecraft-launcher.jar"

# Verificar se o JAR existe
if [ ! -f "$JAR_PATH" ]; then
    echo "ERRO: JAR não encontrado em $JAR_PATH"
    echo "Execute ./build.sh primeiro."
    exit 1
fi

JAVA_BIN="java"
# Tenta localizar JAVA_HOME se definido
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
fi

# Argumentos base para o JavaFX no JAR (Shade)
# Usamos o wrapper Main para evitar problemas de modulos
exec "$JAVA_BIN" \
    -Xmx2G \
    --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED \
    --add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED \
    --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED \
    --add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED \
    --add-opens javafx.base/com.sun.javafx.collections=ALL-UNNAMED \
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
    --add-opens javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED \
    --add-opens javafx.fxml/com.sun.javafx.fxml=ALL-UNNAMED \
    -jar "$JAR_PATH" "$@"
