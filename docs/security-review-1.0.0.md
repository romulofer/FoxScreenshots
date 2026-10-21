# Revisão de segurança — 1.0.0

Feita antes da release 1.0.0 (a reescrita para Kotlin Multiplatform), conforme
[`SPEC.md` §7](../SPEC.md). Escopo: o código de `composeApp/`, as dependências
e os instaladores.

## Resultado

Nenhum problema bloqueante. Os pontos abaixo são riscos aceitos ou limites do
sistema, não defeitos a corrigir antes de publicar.

## O que foi verificado

| Verificação | Resultado |
|---|---|
| **Rede.** Busca por `java.net`, `HttpClient`, `URLConnection`, Ktor, OkHttp | Nenhuma chamada de rede. O único `java.net.URI` interpreta o `file://` que o portal do Wayland devolve. |
| **Processos externos.** Busca por `ProcessBuilder` / `Runtime.exec` | Nenhum no código do app (só os testes ao vivo chamam `xdotool`/`xmessage`, sob xvfb). |
| **Telemetria e logs.** Busca por `println`, `Logger` | O app não registra nada. O JNativeHook, que registraria cada tecla em INFO via `java.util.logging`, é desligado (`Level.OFF`, sem handlers pai). |
| **Captura e arquivos temporários.** | O portal do Wayland grava o PNG num arquivo: o app o lê e o **apaga** em seguida. Fora isso, as imagens só existem em memória, na pasta de saída escolhida ou na área de transferência. |
| **Memória compartilhada X11.** | Não usada (`XGetImage` simples): não há segmento SHM nem arquivo a vazar. |
| **Configurações.** | Um arquivo `.properties` por usuário, gravado de forma atômica (temporário + `move`), com permissão do usuário (0600 no Linux). Só guarda preferências — nunca imagens. |
| **Caminhos de saída.** | O app escolhe o nome do arquivo (carimbo de data/hora, com sufixo `_2`, `_3`… contra colisão) e resolve links simbólicos da pasta antes de gravar: quem chama não injeta segmentos de caminho. |
| **Dependências.** | Compose/Skia, JNA, dbus-java, JNativeHook, FileKit, coroutines — todas do Maven Central, versões fixas em `gradle/libs.versions.toml`. Nenhuma binária de origem desconhecida embutida. |

## Riscos aceitos

- **Gancho global de teclado (JNativeHook).** Para detectar o atalho, a
  biblioteca observa *todas* as teclas do sistema. O app só compara com os três
  atalhos configurados e descarta o resto; nada é gravado nem enviado. No
  macOS isso exige a permissão de Acessibilidade, que o usuário concede
  explicitamente. Ao ser extraída, a biblioteca nativa vai para o diretório
  temporário do usuário (`java.io.tmpdir`).
- **Área de transferência.** Toda captura é copiada para ela de propósito (é o
  que torna o atalho útil). Outros programas do usuário podem lê-la — limite do
  sistema, não do app.
- **Arquivos salvos** herdam a máscara de permissão (`umask`) do usuário, como
  em qualquer programa; quem guarda capturas sensíveis deve escolher a pasta.
- **Windows e macOS sem assinatura digital.** O usuário confere a origem pelo
  repositório (hash/URL da release). O SmartScreen e o Gatekeeper avisam.
- **Backends de Windows e macOS** (GDI, CoreGraphics) não foram exercitados em
  hardware real nesta release; passam pela CI de compilação e testes.

## Pendências sugeridas

- Assinar os instaladores de Windows e macOS.
- Testar os backends de Windows e macOS em máquinas reais antes de anunciá-los
  como suportados sem ressalvas.
