# Contribuindo

Issues e PRs são bem-vindos. Antes de mandar código, leia
[`SPEC.md`](SPEC.md) — é a fonte da verdade para escopo, arquitetura e
limites (§7 lista o que precisa aval prévio e o que nunca entra).

## Ambiente

Veja [README → Como começar](README.md#como-começar) para instalar o SDK do
Flutter e as dependências de sistema. A CI e as releases usam **Flutter
3.47.0 (stable, Dart 3.13.0)** — use a mesma versão local para o `dart
format` não divergir.

```bash
flutter analyze
dart format .
flutter test
flutter test integration_test/all_tests.dart -d linux   # precisa de display/xvfb
```

## Regras do projeto

- **Os dois idiomas sempre completos** — toda chave nova em `app_pt.arb`
  entra também em `app_en.arb` (e vice-versa).
- **Nada de cor crua** — widgets leem `Theme.of(context).extension<FoxColors>()`;
  ver [`lib/core/theme/app_colors.dart`](lib/core/theme/app_colors.dart).
  Mudar a paleta ou a identidade da marca precisa de aval prévio (SPEC §7).
- **Identificadores em inglês**, documentação em português do Brasil (SPEC
  §5).
- **Teste para lógica nova.** Unidade para serviços/modelos, widget para
  telas, e2e quando o fluxo cruzar módulos.
- **Nenhuma chamada de rede, telemetria ou coleta de uso.** O app é local e
  offline — isso nunca muda (SPEC §7).
- Peça aval antes de: dependência pesada/nativa, mudar o backend de captura,
  mudar a paleta/marca, ou tocar no sistema de arquivos fora da pasta de saída
  escolhida.

## Enviando um PR

1. Um PR por mudança lógica; descreva o quê e o porquê.
2. `flutter analyze` e `flutter test` passando localmente.
3. Screenshot ou GIF se a mudança for visual.

## Reportando bugs / pedindo funcionalidades

Use os templates de issue do GitHub. Para vulnerabilidades de segurança, veja
[`SECURITY.md`](SECURITY.md) em vez de abrir issue pública.

---

# Contributing — English

Issues and PRs are welcome. Before sending code, read [`SPEC.md`](SPEC.md) —
it's the source of truth for scope, architecture and limits (§7 lists what
needs prior approval and what never goes in).

## Setup

See [README → Getting started](README.md#getting-started) to install the
Flutter SDK and system dependencies. CI and releases use **Flutter 3.47.0
(stable, Dart 3.13.0)** — use the same version locally so `dart format`
doesn't drift.

```bash
flutter analyze
dart format .
flutter test
flutter test integration_test/all_tests.dart -d linux   # needs a display/xvfb
```

## Project rules

- **Both languages always complete** — every new key in `app_pt.arb` also
  goes into `app_en.arb` (and vice versa).
- **No raw colors** — widgets read
  `Theme.of(context).extension<FoxColors>()`; see
  [`lib/core/theme/app_colors.dart`](lib/core/theme/app_colors.dart).
  Changing the palette or brand identity needs prior approval (SPEC §7).
- **English identifiers**, Portuguese (Brazil) documentation (SPEC §5).
- **Tests for new logic.** Unit for services/models, widget for screens, e2e
  when the flow crosses modules.
- **No network calls, telemetry, or usage collection.** The app is local and
  offline — that never changes (SPEC §7).
- Ask first before: a heavy/native dependency, changing the capture backend,
  changing the palette/brand, or touching the filesystem outside the chosen
  output folder.

## Sending a PR

1. One PR per logical change; describe the what and the why.
2. `flutter analyze` and `flutter test` passing locally.
3. Screenshot or GIF if the change is visual.

## Reporting bugs / requesting features

Use the GitHub issue templates. For security vulnerabilities, see
[`SECURITY.md`](SECURITY.md) instead of opening a public issue.
