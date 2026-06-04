# Arquitetura do MineLauncher

O projeto utiliza uma arquitetura modular baseada em serviços, separando a lógica de negócio, a orquestração de processos e a interface gráfica (JavaFX).

## Visão Geral das Camadas

1.  **UI Layer (`com.minelauncher.ui.controllers`):** Focada apenas em orquestração de eventos JavaFX e bindings com FXML.
2.  **Service Layer (`com.minelauncher.ui.services`):** Contém a lógica de negócio e estados da UI (Auth, FileSystem, Navigation, Launch, State, Installation).
3.  **Launcher Core (`com.minelauncher.launcher`):** Lógica de baixo nível para interação com o Minecraft, detecção de Java e gerenciamento de processos.
4.  **Models/Utils:** Definições de dados e utilitários genéricos.

## Principais Serviços

*   **`AuthService`**: Gerencia o ciclo de vida de autenticação (Microsoft/Offline).
*   **`GameLaunchService`**: Orquestra o lançamento do jogo, incluindo resolução de loader e monitoramento do processo.
*   **`VersionInstallationService`**: Orquestra a instalação de versões e o feedback de progresso na UI.
*   **`LauncherStateService`**: Gerencia o estado visual do launcher (Ready/Busy/Error/Playing).
*   **`FileSystemUIService`**: Operações de IO integradas (abrir pastas, resolver caminhos).
*   **`NavigationService`**: Gerencia a alternância de abas e estilos de navegação.
*   **`WindowService`**: Gerencia operações de janela (minimizar, maximizar, fechar).
*   **`FilterService`**: Lógica de filtragem local de listas (saves, packs, etc).

## Fluxos Críticos

*   **Lançamento:** UI -> `GameLaunchService` -> `VersionManager`/`GameLauncher`.
*   **Navegação:** UI -> `NavigationService` -> Visual Update.
*   **Login:** UI -> `AuthService` -> `MicrosoftAuth`/`SettingsManager`.
