@echo off
set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%target\minecraft-launcher.jar"

java -Xmx2G ^
    --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED ^
    --add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED ^
    --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED ^
    --add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED ^
    --add-opens javafx.base/com.sun.javafx.collections=ALL-UNNAMED ^
    --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED ^
    --add-opens javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED ^
    --add-opens javafx.fxml/com.sun.javafx.fxml=ALL-UNNAMED ^
    -jar "%JAR_PATH%" %*
pause
