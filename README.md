# MineLauncher 🚀

Um launcher de Minecraft moderno, construído com **Java 21+**, **JavaFX** e **Maven**. Projetado para ser rápido, bonito e funcional em Windows, Linux e macOS.

## ✨ Funcionalidades

- **Autenticação Flexível:** Suporte para contas Microsoft (OAuth2) e modo Offline.
- **Gerenciador de Versões:** Instalação fácil de Vanilla, Forge, Fabric, Quilt e NeoForge.
- **Ecossistema de Mods:** Integração com APIs do Modrinth e CurseForge para busca e download.
- **Perfis Personalizados:** Cada perfil pode ter sua própria versão, diretório e configurações de memória.
- **Configuração de Hardware:** Slider para alocação de RAM e detecção automática de instâncias Java.
- **Interface Moderna:** Tema Dark elegante, responsivo e com feedback em tempo real (Console de Logs).
- **Segurança:** Verificação de integridade de arquivos via SHA1.
- **Atalhos Automáticos:** O launcher cria automaticamente atalhos na Área de Trabalho (Windows) ou no Menu de Aplicativos (Linux) ao ser iniciado.

## 🛠️ Pré-requisitos

- **Java 21 ou superior** com JavaFX incluído.
- **Maven 3.8+** (apenas para quem for compilar o código).

### Onde baixar o Java (Recomendado)
Para evitar erros de "JavaFX not found", recomendamos o **BellSoft Liberica JDK (FULL Version)**, que já vem com tudo pronto:

#### **Linux (Terminal)**
```bash
# Baixa e extrai o JDK 21 FULL
curl -sL https://download.bell-sw.com/java/21.0.11+11/bellsoft-jdk21.0.11+11-linux-amd64-full.tar.gz | tar xz
export JAVA_HOME=$(pwd)/jdk-21.0.11-full
export PATH=$JAVA_HOME/bin:$PATH
```

#### **Windows**
- Baixe e instale o instalador MSI: [bellsoft-jdk21.0.11+11-windows-amd64.msi](https://download.bell-sw.com/java/21.0.11+11/bellsoft-jdk21.0.11+11-windows-amd64.msi)
- *Certifique-se de marcar a opção "Add to PATH" durante a instalação.*

#### **macOS**
- **Apple Silicon (M1/M2/M3):** [bellsoft-jdk21.0.11+11-macos-aarch64.dmg](https://download.bell-sw.com/java/21.0.11+11/bellsoft-jdk21.0.11+11-macos-aarch64.dmg)
- **Intel (x64):** [bellsoft-jdk21.0.11+11-macos-amd64-full.dmg](https://download.bell-sw.com/java/21.0.11+11/bellsoft-jdk21.0.11+11-macos-amd64-full.dmg)

---

## 🚀 Como Começar

### 1. Build do Projeto
Para compilar e gerar o JAR executável, use o script de build:

```bash
chmod +x build.sh
./build.sh
```
*Isso gerará o arquivo `target/minecraft-launcher-1.0.0.jar`.*

### 2. Execução
Após o build, você pode iniciar o launcher usando o script de execução:

**Linux / macOS:**
```bash
chmod +x launcher.sh
./launcher.sh
```

**Windows:**
```batch
launcher.bat
```

> **Nota:** Na primeira execução, o launcher criará automaticamente os atalhos no seu sistema para facilitar os próximos acessos.

## 📁 Estrutura do Projeto

- `src/main/java/com/minelauncher/`
    - `Main.java`: Wrapper de inicialização (evita problemas de módulos JavaFX).
    - `MineLauncher.java`: Classe principal da aplicação JavaFX.
    - `auth/`: Lógica de autenticação Microsoft e Offline.
    - `launcher/`: Gerenciamento de downloads, versões e execução do jogo.
    - `mods/`: Integração com Modrinth/CurseForge.
    - `utils/ShortcutManager.java`: Criador automático de atalhos (Windows/Linux).
- `src/main/resources/`
    - `fxml/`: Arquivos de layout da interface.
    - `css/`: Estilização (Tema Dark).
    - `images/`: Recursos visuais e logos.

## 🔨 Comandos de Build Avançados

O script `build.sh` aceita os seguintes parâmetros:
- `./build.sh package`: Ação padrão, gera o JAR.
- `./build.sh run`: Compila e já abre o launcher.
- `./build.sh clean`: Limpa os arquivos temporários de build.
- `./build.sh installer`: Gera um instalador nativo (.deb, .exe, .dmg) usando `jpackage`.

## 🤝 Contribuição

Sinta-se à vontade para abrir issues ou enviar pull requests. Toda ajuda é bem-vinda!

## 📜 Licença

Distribuído sob a licença MIT. Veja `LICENSE` para mais informações.
