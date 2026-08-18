# Política de segurança

FoxScreenShots roda **100% localmente**: não faz chamadas de rede, não coleta
telemetria e nunca envia o conteúdo da tela para lugar nenhum (ver
[`SPEC.md` §7](SPEC.md)). Mesmo assim, screenshots podem conter dados
sensíveis, então falhas que exponham esses dados são levadas a sério.

## Versões suportadas

Só a última versão marcada (release) recebe correções de segurança. Não há
suporte a versões antigas.

## Como reportar uma vulnerabilidade

Prefira o
[relatório privado de vulnerabilidade do GitHub](https://github.com/romulofer/FoxScreenShots/security/advisories/new)
(aba **Security** do repositório). Se preferir, envie um e-mail para
**rfe89@hotmail.com** com:

- Passos para reproduzir.
- Versão do app e sistema operacional.
- Impacto esperado (que dado vaza, em que condição).

Não abra uma *issue* pública para vulnerabilidades ainda não corrigidas.

Você recebe uma resposta em até 7 dias. Correções aceitas geram uma nova
release e crédito no changelog da release, a menos que você peça anonimato.

---

# Security policy — English

FoxScreenShots runs **entirely locally**: no network calls, no telemetry, and
your screen content never leaves the machine (see [`SPEC.md` §7](SPEC.md)).
Screenshots can still hold sensitive data, so anything that leaks it is taken
seriously.

## Supported versions

Only the latest tagged release gets security fixes. No support for older
versions.

## Reporting a vulnerability

Prefer
[GitHub's private vulnerability reporting](https://github.com/romulofer/FoxScreenShots/security/advisories/new)
(the repository's **Security** tab). Otherwise, email **rfe89@hotmail.com**
with:

- Steps to reproduce.
- App version and operating system.
- Expected impact (what data leaks, under which condition).

Please don't open a public issue for an unfixed vulnerability.

You'll get a response within 7 days. Accepted fixes ship in a new release and
get credited in that release's changelog, unless you ask to stay anonymous.
