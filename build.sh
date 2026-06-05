#!/bin/bash
set -e

echo "==============================="
echo " MineLauncher - Build Script"
echo "==============================="

# Tenta localizar Java 21+ se o padrão do sistema for antigo
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    for jvm in /usr/lib/jvm/java-21-temurin /usr/lib/jvm/java-26-openjdk /usr/lib/jvm/java-25-openjdk; do
        if [ -d "$jvm" ]; then
            export JAVA_HOME="$jvm"
            break
        fi
    done
fi

JAVA_EXE=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA_EXE=${JAVA_EXE:-java}

# Verificar Java
if ! "$JAVA_EXE" -version &> /dev/null; then
    echo "ERRO: Java não encontrado ($JAVA_EXE). Instale JDK 21+ ou defina JAVA_HOME."
    exit 1
fi

JAVA_VERSION=$("$JAVA_EXE" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
[ "$JAVA_VERSION" = "1" ] && JAVA_VERSION=$("$JAVA_EXE" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f2)

echo "Java versão: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "ERRO: Java 21+ é necessário. Versão atual: $JAVA_VERSION"
    exit 1
fi

# Verificar Maven
if ! command -v mvn &> /dev/null; then
    echo "ERRO: Maven não encontrado. Instale Maven 3.8+."
    exit 1
fi

echo "Maven: $(mvn -version 2>&1 | head -n 1)"
echo ""

# Ação padrão agora é 'package' para criar o JAR
ACTION="${1:-package}"

case "$ACTION" in
    build|package)
        echo "Gerando JAR executável..."
        mvn clean package -DskipTests
        echo ""
        echo "Build concluído! JAR gerado em: target/minecraft-launcher.jar"
        echo "Para rodar, use: ./launcher.sh"
        ;;
    run)
        echo "Compilando e Executando..."
        mvn clean package -DskipTests
        ./launcher.sh
        ;;
    installer)
        echo "Criando instalador..."
        mvn clean package jpackage:jpackage -DskipTests
        echo ""
        echo "Instalador em: target/jpackage/"
        ;;
    clean)
        echo "Limpando..."
        mvn clean
        echo "Limpo!"
        ;;
    *)
        echo "Uso: $0 {build|package|run|installer|clean}"
        echo ""
        echo "  build/package - Gera o JAR executável (padrão)"
        echo "  run           - Gera o JAR e executa o launcher"
        echo "  installer     - Cria instalador nativo com jpackage"
        echo "  clean         - Limpa arquivos de build"
        ;;
esac
