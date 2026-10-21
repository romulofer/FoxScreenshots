# Contribuindo

Issues e PRs são bem-vindos. Antes de mandar código, abra uma issue para
conversar: mudanças grandes (dependência nova, backend de captura, paleta de
cores) precisam de aval prévio, e o app nunca faz chamadas de rede nem envia
conteúdo da tela para fora da máquina.

## Ambiente

Veja [README → Desenvolvimento](README.md#desenvolvimento): JDK 25+ e o
wrapper do Gradle bastam.

```bash
./gradlew :composeApp:desktopTest
FOX_ISOLATED_DISPLAY=1 xvfb-run -a ./gradlew :composeApp:desktopTest   # inclui os testes ao vivo (X11, atalhos, área de transferência)
```

## Regras do projeto

- **Os dois idiomas sempre completos** — todo texto novo entra na interface
  `Strings` e nas duas implementações (`StringsPt`, `StringsEn`); o compilador
  recusa um idioma que esqueça uma chave.
- **Nada de cor crua** — as telas leem `LocalFoxColors.current` ou o tema do
  Material; ver [`FoxColors.kt`](composeApp/src/commonMain/kotlin/com/foxdevelops/foxscreenshots/core/theme/FoxColors.kt).
  Mudar a paleta ou a identidade da marca precisa de aval prévio (SPEC §7).
- **Identificadores em inglês**, documentação em português do Brasil (SPEC
  §5).
- **Lógica fora do sistema operacional fica em `commonMain`**, atrás de
  interfaces; o que toca no sistema fica em `desktopMain`.
- **Teste para lógica nova.** Unidade para serviços/modelos, Compose UI para
  telas, ao vivo (sob xvfb) quando o fluxo cruzar o sistema.
- **Nenhuma chamada de rede, telemetria ou coleta de uso.** O app é local e
  offline — isso nunca muda (SPEC §7).
- Peça aval antes de: dependência pesada/nativa, mudar o backend de captura,
  mudar a paleta/marca, ou tocar no sistema de arquivos fora da pasta de saída
  escolhida.

## Enviando um PR

1. Um PR por mudança lógica; descreva o quê e o porquê.
2. `./gradlew :composeApp:desktopTest` passando localmente.
3. Screenshot ou GIF se a mudança for visual.

## Reportando bugs / pedindo funcionalidades

Use os templates de issue do GitHub. Para vulnerabilidades de segurança, use o
[relatório privado do GitHub](https://github.com/romulofer/FoxScreenShots/security/advisories/new) em vez de abrir issue pública.

---

# Contributing — English

Issues and PRs are welcome. Before sending code, open an issue to talk it
over: larger changes (new dependency, capture backend, color palette) need
prior approval, and the app never makes network calls or sends screen content
off the machine.

## Setup

See [README → Development](README.md#development): JDK 25+ and the Gradle
wrapper are all you need.

```bash
./gradlew :composeApp:desktopTest
FOX_ISOLATED_DISPLAY=1 xvfb-run -a ./gradlew :composeApp:desktopTest   # includes the live tests (X11, hotkeys, clipboard)
```

## Project rules

- **Both languages always complete** — every new string goes into the
  `Strings` interface and both implementations (`StringsPt`, `StringsEn`); the
  compiler rejects a language that misses a key.
- **No raw colors** — screens read `LocalFoxColors.current` or the Material
  theme; see
  [`FoxColors.kt`](composeApp/src/commonMain/kotlin/com/foxdevelops/foxscreenshots/core/theme/FoxColors.kt).
  Changing the palette or brand identity needs prior approval (SPEC §7).
- **English identifiers**, Portuguese (Brazil) documentation (SPEC §5).
- **OS-independent logic lives in `commonMain`**, behind interfaces; what
  touches the OS lives in `desktopMain`.
- **Tests for new logic.** Unit for services/models, Compose UI for screens,
  live (under xvfb) when the flow crosses the OS.
- **No network calls, telemetry, or usage collection.** The app is local and
  offline — that never changes (SPEC §7).
- Ask first before: a heavy/native dependency, changing the capture backend,
  changing the palette/brand, or touching the filesystem outside the chosen
  output folder.

## Sending a PR

1. One PR per logical change; describe the what and the why.
2. `./gradlew :composeApp:desktopTest` passing locally.
3. Screenshot or GIF if the change is visual.

## Reporting bugs / requesting features

Use the GitHub issue templates. For security vulnerabilities, use GitHub's
[private vulnerability reporting](https://github.com/romulofer/FoxScreenShots/security/advisories/new) instead of opening a public issue.
