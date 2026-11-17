# Relações

App Android nativa para manter contacto com as pessoas que lhe importam, pessoais ou profissionais. A app não obriga a nada, mas incentiva o contacto com lembretes, sugestões e ações rápidas. Funciona sem internet, não tem publicidade e os dados ficam no telemóvel. Requer Android 8.0 ou superior.

## Funcionalidades

- **Pessoas**: criadas à mão ou importadas dos contactos do telemóvel (opcional), com foto, telefone, email, aniversário, notas e grupos (Família, Amigos, Trabalho… configuráveis; uma pessoa pode estar em vários). A lista mostra a data e o meio do último contacto, filtra por grupo, pesquisa e ordena por tempo sem contacto.
- **Registo de contactos**: data (pode ser passada), meio (chamada, SMS, WhatsApp, Facebook, Messenger, Instagram, pessoalmente, email, outro) e nota. O histórico fica na ficha da pessoa.
- **Lembretes (modelo unificado)**: únicos ou recorrentes, com texto livre, vários por pessoa. Insistem com a periodicidade escolhida até serem marcados como feitos, sem insistir durante a noite. A notificação tem as ações **Feito**, **Adiar** e **Já falei**.
  - **Manter contacto**: frequência por pessoa (ex.: mensal). O prazo conta a partir do último contacto registado. Em "Já falei" indica-se a data real e o meio, e o próximo aviso é recalculado.
  - **Aniversários**: aviso no próprio dia e/ou X dias antes, configurável por pessoa, e lembretes extra com texto próprio (ex.: comprar prenda 7 dias antes). Insistem até serem feitos. 29 de fevereiro conta como 28 nos anos comuns.
- **Mensagens de aniversário**: no dia, a app sugere 3 a 4 mensagens da biblioteca (curtas ou longas, conforme a preferência da pessoa) e evita as já enviadas. A mensagem pode ser editada e enviada por WhatsApp, SMS ou pelo menu de partilha, e fica registada no histórico com a data.
- **Biblioteca de modelos**: modelos incluídos por tipo (aniversário, saudade, profissional) e tamanho (curta, longa). O utilizador pode criar, editar e apagar modelos. Variáveis: `{nome}`, `{primeiro_nome}`, `{idade}`. Sem IA nem APIs pagas.
- **Incentivo**:
  - Ações rápidas: ligar, WhatsApp, SMS ou email com um toque, já com mensagem sugerida. Ao voltar, a app propõe registar o contacto.
  - Saúde da relação: anel verde, amarelo ou vermelho conforme o tempo desde o último contacto face à frequência.
  - Sugestões diárias: notificação de manhã com 2 a 4 pessoas a contactar hoje.
  - Metas semanais (ex.: 5 pessoas por semana) e série de semanas seguidas cumpridas.
  - Temas de conversa: antes de contactar, mostra as notas, a última interação, as pessoas ligadas e os gostos.
  - Widget com as pessoas há mais tempo sem contacto e os próximos aniversários.
- **Ligações entre pessoas** (cônjuge, filho, pai/mãe, colega…), guardadas nos dois sentidos e mostradas nos temas de conversa.
- **Preferências e ideias de prenda** por pessoa, mostradas nos avisos de aniversário.
- **Resumo mensal**: notificação no dia 1 e ecrã com quem contactou, quem ficou atrasado, aniversários do mês seguinte e evolução das metas.
- **Cópia de segurança**: a cópia automática do Android guarda os dados no Google Drive da conta Google do telemóvel e repõe-nos ao reinstalar ou num telemóvel novo, sem login na app. Há também exportação e restauro manual em JSON, com as fotos incluídas; o Google Drive pode ser escolhido como destino.
- Tema claro, escuro ou automático.

## Ecrãs

Hoje · Pessoas · Lembretes · Modelos (navegação inferior), Ficha da pessoa, Mensagem de aniversário, Resumo mensal e Definições (cópia de segurança, horários das notificações, metas, grupos, importação, tema).

## Estrutura

```
AndroidManifest.xml
res/                     recursos (cores, temas, ícones, widget, regras de cópia de segurança)
src/pt/relacoes/core/    lógica pura e testável (datas, aniversários, recorrência, saúde, metas, modelos)
src/pt/relacoes/data/    SQLite, preferências, fotos, cópia de segurança, cálculos para os ecrãs
src/pt/relacoes/notify/  motor dos lembretes, alarmes e notificações
src/pt/relacoes/ui/      ecrãs e widget
test/                    testes da lógica (JVM)
tools/zipalign.py        alinhamento do APK
build.sh                 compilação sem Gradle
```

A app usa só o framework Android, sem bibliotecas externas.

## Compilar no GitHub (Actions)

O workflow `.github/workflows/build.yml` corre os testes da lógica e compila a app:

- **APK de preview sem assinatura**: em cada push para `main` e nas execuções manuais normais (artefacto `Relacoes-apk-preview-unsigned`).
- **APK assinado**: em **Actions → Build Android APK → Run workflow**, com a opção **Gerar um APK assinado** marcada, ou num push para `main` com `[signed-apk]` na mensagem do commit (artefacto `Relacoes-apk-assinado`).
- **Release**: uma tag `v*` (ex.: `v1.0.0`) gera o APK assinado e cria uma Release.

Os APKs assinados precisam destes *Repository secrets* em **Settings → Secrets and variables → Actions**:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o ficheiro `release.jks` em base64 (`base64 -w0 release.jks`) |
| `KS_PASS` | a password da keystore e da chave |

A chave usa o alias `relacoes` (pode ser alterado com a variável `KS_ALIAS`). Guarde a keystore num local seguro: as atualizações da app têm de ser assinadas sempre com a mesma chave.

## Compilar localmente

Requisitos: JDK 17 ou superior, Python 3 e o Android SDK (build-tools 35 e platform android-35).

```bash
export ANDROID_HOME=~/Android/Sdk
export KS_PASS='a-sua-password'
cp /caminho/seguro/release.jks .   # a chave de assinatura (não está no repositório)
./build.sh                          # gera build/Relacoes.apk
UNSIGNED=1 ./build.sh               # ou um APK de preview sem assinatura
```

## Testes

```bash
mkdir -p build/test
javac -encoding UTF-8 -d build/test src/pt/relacoes/core/*.java test/*.java
java -cp build/test CoreTest
```

## Fora do âmbito da v1

- Deteção automática de chamadas, SMS ou mensagens de outras apps.
- Geração de mensagens com IA.
- Login Google e Drive API: substituídos pela cópia automática do Android e pela exportação manual (não precisam de configuração no Google Cloud).
