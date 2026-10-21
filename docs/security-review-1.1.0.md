# Revisão de segurança — 1.1.0

Feita antes da release 1.1.0, conforme [`SPEC.md` §7](../SPEC.md). Escopo: o que
mudou desde a [1.0.0](security-review-1.0.0.md) — bandeja por D-Bus, pasta da
biblioteca do gancho de teclado, abertura de imagem, nome de arquivo configurável
— mais a repetição das buscas gerais.

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
