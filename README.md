# MineLauncher 🚀

Um launcher de Minecraft moderno, construído com **Java 21+**, **JavaFX** e **Maven**. Projetado para ser rápido, bonito e funcional em Windows, Linux e macOS.

## ✨ Funcionalidades

- **Autenticação Flexível:** Suporte para contas Microsoft (OAuth2) e modo Offline.
- **Gerenciador de Versões:** Instalação fácil de Vanilla, Forge, Fabric, Quilt e NeoForge.
- **Ecossistema de Mods:** Integração com APIs do Modrinth e CurseForge para busca e download.
- **Perfis Personalizados:** Cada perfil pode ter sua própria versão, diretório e configurações de memória.
- **Configuração de Hardware:** Slider para alocação de RAM e detecção automática de instâncias Java.
- **Interface Moderna:** Tema Dark elegante, responsivo e com feedback em tempo real.
- **Segurança:** Verificação de integridade de arquivos via SHA1 e armazenamento seguro de tokens (chave criptográfica única por instalação).
- **Atalhos Automáticos:** Criação automática de atalhos no sistema.

## 🛠️ Pré-requisitos

- **Java 21 ou superior** com JavaFX incluído.
- **Maven 3.8+** (apenas para compilação).

*(Instruções de instalação de JDK omitidas por brevidade - consulte a wiki oficial de Java para sua distro).*

---

## 🚀 Como Começar

### 1. Build do Projeto
Para compilar e gerar o JAR executável:

```bash
chmod +x build.sh
./build.sh
```

### 2. Execução
**Linux / macOS:** `./launcher.sh`
**Windows:** `launcher.bat`

---

## 🏗️ Arquitetura do Projeto

O projeto passou por uma refatoração arquitetural (estilo "Clean Architecture" simplificado) para separar responsabilidades e garantir manutenibilidade:

### Visão Geral das Camadas
1. **UI Layer (`com.minelauncher.ui.controllers`):** Orquestração leve de eventos JavaFX.
2. **Service Layer (`com.minelauncher.ui.services`):** Lógica de negócio e estado (Auth, FileSystem, Navigation, Launch, Installation, etc.).
3. **Launcher Core (`com.minelauncher.launcher`):** Interação com Minecraft (instalação, execução, gerenciamento de processos).
4. **Models/Utils:** Definições de dados e utilitários genéricos.

### Principais Serviços
- `AuthService`: Ciclo de vida de autenticação.
- `GameLaunchService`: Orquestração de lançamento e monitoramento.
- `VersionInstallationService`: Instalação de versões e feedback.
- `LauncherStateService`: Gerenciamento de estado da UI.
- `FileSystemUIService`: Operações de IO integradas.
- `NavigationService`: Gerenciamento de navegação e abas.
- `WindowService`: Operações de janela.
- `FilterService`: Filtragem local de listas.

---

## ⚙️ Configuração Avançada

Para alterar endpoints (API URLs, Proxy) sem precisar recompilar o código, crie um arquivo `launcher.properties` na pasta base do launcher (geralmente `~/.minecraft/`):

```properties
# Exemplo de conteúdo
curseforge.proxy.url=https://minecraft-launcher-java.vercel.app/api/cf
modrinth.api.url=https://api.modrinth.com/v2
```

---

## 🔨 Comandos de Build
- `./build.sh package`: Gera o JAR.
- `./build.sh run`: Compila e executa.
- `./build.sh clean`: Limpa o projeto.

## 🤝 Contribuição
Sinta-se à vontade para abrir issues ou enviar pull requests.

## 📜 Licença
Distribuído sob a licença MIT.
