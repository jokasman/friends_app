# Amigos

App Android em português para manter o contacto com os amigos: lembra os aniversários e avisa quando já passou demasiado tempo sem falar com alguém. Não tem contas, publicidade nem acesso à internet, e os dados ficam só no telemóvel. Requer Android 8.0 ou superior.

## Funcionalidades

- **Lista de amigos** com nome, telefone, email, aniversário e notas. O nome e o telefone podem ser importados dos contactos do telemóvel, sem pedir acesso a todos os contactos.
- **Aniversários próximos**: os próximos 30 dias no ecrã principal, com a idade que vão fazer quando o ano é conhecido. 29 de fevereiro conta como 28 nos anos comuns.
- **Lembrar de falar**: para cada amigo, de quanto em quanto tempo quer dar notícias (semanal, quinzenal, mensal, de 2 em 2 meses, trimestral, semestral ou anual). A secção "Falar em breve" mostra os que estão em atraso ou a vencer.
- **Registo de contactos**: conversa, chamada, mensagem ou encontro, com data e nota. Ao voltar de uma chamada, SMS, WhatsApp ou email aberto a partir da app, pergunta se quer registar o contacto. Toque sem largar num amigo na lista para registar que falou hoje.
- **Histórico** de contactos por amigo.
- **Aviso diário** à hora escolhida: aniversários (no dia ou com 1, 3 ou 7 dias de antecedência) e amigos à espera de notícias.
- **Pesquisa**, **tema claro, escuro ou automático** e **cópia de segurança** em JSON.

## Estrutura

```
AndroidManifest.xml
res/                     recursos (cores, temas, ícones)
src/pt/amigos/core/      lógica pura e testável (datas, aniversários, atrasos)
src/pt/amigos/data/      SQLite, preferências, cópia de segurança
src/pt/amigos/notify/    aviso diário e reagendamento no arranque
src/pt/amigos/ui/        ecrãs
test/                    testes da lógica (JVM)
tools/zipalign.py        alinhamento do APK
build.sh                 compilação sem Gradle
```

A app usa só o framework Android, sem bibliotecas externas.

## Compilar localmente

Requisitos: JDK 17 ou superior, Python 3 e o Android SDK (build-tools 35 e platform android-35).

```bash
export ANDROID_HOME=~/Android/Sdk
export KS_PASS='a-sua-password'
cp /caminho/seguro/release.jks .   # a chave de assinatura (não está no repositório)
./build.sh                          # gera build/Amigos.apk
UNSIGNED=1 ./build.sh               # ou um APK de preview sem assinatura
```

## Compilar no GitHub (Actions)

O workflow `.github/workflows/build.yml` gera um APK de preview sem assinatura nos pushes para `main` e nas execuções manuais normais. Para obter um APK assinado sem criar uma Release, execute manualmente o workflow em **Actions → Build Android APK → Run workflow** e marque a opção **Gerar um APK assinado**. Também é possível iniciar esse build num push para `main` incluindo `[signed-apk]` na mensagem do commit. O APK aparece como artefacto `Amigos-apk-assinado`. Uma tag `v*` (por exemplo `v1.0.0`) gera um APK assinado e cria uma Release.

Para os APKs assinados, crie estes *Repository secrets* em **Settings → Secrets and variables → Actions**:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o ficheiro `release.jks` em base64 (`base64 -w0 release.jks`) |
| `KS_PASS` | a password da keystore e da chave |

A chave usa o alias `amigos` (pode ser alterado com a variável `KS_ALIAS` no `build.sh`). Guarde a keystore num local seguro: as atualizações da app têm de ser assinadas sempre com a mesma chave.

Para criar uma chave nova:

```bash
keytool -genkeypair -keystore release.jks -alias amigos -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Amigos, C=PT"
```

## Testes

```bash
mkdir -p build/test
javac -d build/test src/pt/amigos/core/*.java test/*.java
java -cp build/test ScheduleTest
```
