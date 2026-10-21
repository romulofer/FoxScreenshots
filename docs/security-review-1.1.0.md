# Revisão de segurança — 1.1.0

Feita antes da release 1.1.0, conforme [`SPEC.md` §7](../SPEC.md). Escopo: o que
mudou desde a [1.0.0](security-review-1.0.0.md) — bandeja por D-Bus, pasta da
biblioteca do gancho de teclado, abertura de imagem, nome de arquivo configurável,
e a rodada de UX (toast, seleção ajustável, Esc global, "Mostrar na pasta",
gravação de atalhos, editor com seleção/zoom) — mais a repetição das buscas gerais.

## Resultado

Nenhum problema bloqueante.

## O que foi verificado

| Verificação | Resultado |
|---|---|
| **Rede.** Busca por `java.net` (HTTP/URL/Socket), `HttpClient`, OkHttp, Ktor | Nenhuma chamada de rede. |
| **Processos externos.** `ProcessBuilder` / `Runtime.exec` | Nenhum no código do app. |
| **Telemetria e logs.** `println`, `Logger` | Só duas linhas em `stderr`, quando a bandeja ou os atalhos não sobem, com o motivo técnico (nome de exceção/mensagem). Nunca teclas nem conteúdo de tela. |
| **Bandeja por D-Bus.** | O item exporta só `org.kde.StatusNotifierItem` e `com.canonical.dbusmenu`, no barramento **de sessão** do próprio usuário. As propriedades são somente leitura (o `Set` é recusado) e o menu só dispara as ações que a bandeja já tinha (mostrar, capturas, configurações, sair). O ícone vai como pixmap: nenhum arquivo temporário. |
| **Nome de arquivo configurável.** | `renderFileName` descarta separadores de caminho, caracteres reservados do Windows e de controle, `..` nas pontas e limita o tamanho; um padrão vazio volta ao padrão. Testado com `../etc/passwd`. O serviço continua resolvendo a pasta antes de gravar. |
| **Abrir imagem.** | O arquivo é lido por escolha explícita do usuário no diálogo nativo, com teto de 64 MiB; é decodificado pelo Skia e regravado como PNG. Falha de decodificação vira mensagem, nunca exceção solta. |
| **Biblioteca do gancho de teclado.** | Passa a ser desempacotada em `~/.config/foxscreenshots/native` (equivalentes no Windows e macOS) em vez da pasta de instalação, que é somente leitura. Ver riscos aceitos. |
| **Esc global.** | O gancho de teclado já observava todas as teclas; agora o Esc também é uma "ação" fixa (cancelar o temporizador). Fora de uma contagem regressiva ela não faz nada, e nenhuma tecla é gravada nem enviada. Esc e letras soltas são recusados como atalhos de captura, para o gancho não disparar enquanto se digita. |
| **Gravar atalho.** | Lê a tecla pressionada na própria janela do app (evento do Compose), não pelo gancho do sistema. O valor guardado é só o rótulo (`Ctrl+Shift+K`). |
| **Mostrar na pasta.** | Usa `java.awt.Desktop` sobre um arquivo que o próprio app salvou (caminho guardado em memória, por captura): `browseFileDirectory` onde existe, senão abre a pasta pai. Nenhum `ProcessBuilder` no código do app; arquivo inexistente devolve falha sem lançar. |
| **Toast e selo.** | Janelas próprias, sem foco e sem rede, com texto fixo da interface; o toast é fechado antes de qualquer nova captura, para não aparecer na imagem. |
| **Configurações novas.** | `selection_confirm` e `dismissed_issues` (nomes de tipos de aviso) no mesmo `.properties` por usuário: preferências, nunca imagens. |
| **Desfazer exclusão.** | A captura apagada fica em memória até o aviso sumir ou a sessão limpar; nada vai a disco. |
| **Dependências.** | Sem dependência nova: dbus-java, JNativeHook, JNA, FileKit e Compose já estavam. Versões fixas em `gradle/libs.versions.toml`. |

## Riscos aceitos

- **Biblioteca nativa numa pasta do usuário.** O JNativeHook carrega uma `.so`/`.dll`
  extraída para uma pasta que o usuário escreve; quem já roda código como esse
  usuário poderia trocá-la. Isso não amplia o que ele já pode fazer (o mesmo
  acesso alteraria o próprio app ou o `~/.bashrc`), e a alternativa — pasta
  somente leitura — deixava os atalhos globais quebrados. A pasta é criada pelo
  app com as permissões padrão do usuário.
- **Gancho global de teclado, área de transferência, arquivos salvos, falta de
  assinatura em Windows/macOS e backends não exercitados em hardware real:**
  inalterados desde a 1.0.0.

## Pendências sugeridas

- Verificar o hash da biblioteca extraída contra a do `.jar` antes de carregá-la.
- Assinar os instaladores de Windows e macOS.
