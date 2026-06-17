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

# JavaFX e dependências estão empacotados no fat JAR (shade),
# então --add-opens para módulos do JavaFX não são necessários
exec "$JAVA_BIN" -Xmx2G -jar "$JAR_PATH" "$@"
