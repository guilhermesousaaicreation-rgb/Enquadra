# Enquadra (Android)

App de câmara com guia de enquadramento, controlos manuais, looks de película e molduras.
Requer Android 10 ou mais recente.

## Como obter o APK sem instalar nada no computador (GitHub)
1. Cria uma conta gratuita em github.com e um repositório novo (botão «New»).
2. Em «uploading an existing file», arrasta TODO o conteúdo desta pasta (incluindo a pasta `.github`).
   Se a pasta `.github` não subir (é oculta): «Add file» > «Create new file», escreve o nome
   `.github/workflows/build.yml` e cola o conteúdo do ficheiro com o mesmo nome deste projeto.
3. Separador «Actions»: o «Build APK» arranca sozinho (demora 3 a 6 minutos).
4. Quando ficar verde, abre-o e descarrega «Enquadra-APK» (um .zip com o app-debug.apk dentro).
5. Passa o .apk para o telemóvel, abre-o e permite «instalar de fontes desconhecidas» quando pedir.

Se o passo 3 falhar (círculo vermelho), abre o registo, copia a mensagem de erro e cola-a no Claude para ser corrigida.

## Alternativa: Android Studio
Abre esta pasta no Android Studio (File > Open), espera pela sincronização e usa Build > Build APK(s).
