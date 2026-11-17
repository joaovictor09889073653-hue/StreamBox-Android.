# StreamBox Android — Parte 3: autenticação real

Esta entrega conecta o app Android a uma API FastAPI e ao PostgreSQL.

## O que está implementado
- Cadastro de usuário por e-mail e senha.
- Login com validação de senha.
- Senhas armazenadas com hash Argon2 (nunca em texto puro).
- JWT assinado e com expiração.
- Endpoint protegido `GET /me`.
- Persistência dos usuários no PostgreSQL.
- App Android usando Retrofit + Gson para falar com a API.
- Token guardado localmente via Preferences DataStore e removido no logout.
- Tela de login/cadastro integrada ao backend e restauração de sessão.

## 1. Subir o PostgreSQL
Na raiz desta entrega:
```bash
docker compose up -d
```
O banco local usa:
- database: `streambox`
- user: `streambox`
- password: `change-me-local`

Altere esses dados antes de qualquer ambiente compartilhado/produção.

## 2. Configurar e iniciar a API
```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
```
Copie `.env.example` para `.env` e substitua `JWT_SECRET` por um segredo aleatório forte.
Depois:
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
Teste `http://127.0.0.1:8000/health` e a documentação em `http://127.0.0.1:8000/docs`.

**Atenção:** `DATABASE_URL` em `.env` deve apontar para o PostgreSQL. A API não usa mais armazenamento em memória.

## 3. Abrir o Android
Abra a pasta `android/` no Android Studio e sincronize o Gradle.

A URL padrão no app é `http://10.0.2.2:8000/`, que funciona no emulador Android quando a API está rodando no computador.
Para aparelho físico, altere `API_BASE_URL` em `android/app/build.gradle.kts` para o IP LAN do computador, por exemplo `http://192.168.1.20:8000/` (os dois aparelhos precisam estar na mesma rede).

O manifest permite HTTP apenas para facilitar desenvolvimento local. **Não publique assim.** Em produção use HTTPS, desative cleartext traffic e configure domínio/certificado.

## 4. Fluxo de teste
1. Inicie PostgreSQL.
2. Inicie a API.
3. Rode o app no emulador.
4. Toque em "Criar conta", informe e-mail e senha (mínimo 8 caracteres).
5. Faça login; o app valida o JWT em `/me` e abre a tela inicial.
6. Use "Sair" para apagar o token local.

## Limitações e próximos passos antes de produção
- O banco cria a tabela automaticamente no startup; adotar Alembic para migrações versionadas.
- Adicionar recuperação/verificação de e-mail, política de senha, rate limiting e proteção contra abuso.
- Guardar o token com armazenamento criptografado apoiado pelo Android Keystore; DataStore nesta versão guarda o token no armazenamento privado do app, mas não criptografado.
- Configurar HTTPS, gestão de segredos, backups, observabilidade e testes automatizados.
- Implementar autorização por perfil/assinatura no servidor.
- Esta etapa não inclui listas/canais nem reprodução: o player e a ingestão de listas autorizadas serão etapas posteriores.

Use somente listas e transmissões que você tenha autorização para acessar ou distribuir.


# Parte 4 — listas M3U autorizadas

Esta versão mantém o cadastro/login da Parte 3 e acrescenta importação e gerenciamento de listas M3U. A importação é feita colando o conteúdo do arquivo M3U no app; não há busca automática de listas nem catálogo de terceiros.

## Recursos
- Importação de conteúdo M3U no menu **TV ao vivo** do Android.
- Parser de `#EXTINF` com `tvg-name`, `tvg-id`, `tvg-logo` e `group-title`.
- Armazenamento de listas e canais no PostgreSQL, separados por usuário.
- Listagem, visualização dos canais e exclusão de listas.
- Limites de 5 MB e 10.000 canais por importação.
- Só aceita URLs de transmissão HTTP/HTTPS no conteúdo M3U.
- Rotas protegidas por JWT.

## Rotas da API
- `POST /playlists/import?name=Minha%20lista` — corpo `text/plain` com o conteúdo M3U.
- `GET /playlists` — listas do usuário autenticado.
- `GET /playlists/{id}/channels` — canais da lista do próprio usuário.
- `DELETE /playlists/{id}` — exclui a lista e seus canais.

Todas exigem `Authorization: Bearer <token>`. A API nunca baixa automaticamente URLs de listas, reduzindo riscos de SSRF. As URLs dos canais são armazenadas como fornecidas; a reprodução de vídeo será uma etapa separada.

## Como usar pelo celular
1. Atualize o backend com esta Parte 4 e reinicie a API.
2. Atualize/instale o app Android compilado com esta versão.
3. Entre na sua conta e abra **TV ao vivo**.
4. Cole o texto de uma lista M3U que você tenha autorização para usar.
5. Dê um nome e toque em **IMPORTAR LISTA**.
6. Use **Ver canais** para consultar os canais ou **Excluir** para remover a lista.

## Exemplo M3U de teste (não transmite conteúdo)
```m3u
#EXTM3U
#EXTINF:-1 tvg-id="demo.one" tvg-name="Canal Demonstração" group-title="Teste",Canal Demonstração
https://example.com/stream-demo.m3u8
```

## Observações técnicas
- O banco cria as novas tabelas no startup, seguindo a estratégia provisória da Parte 3. Para produção, adote migrações Alembic.
- O app mostra e importa os canais, mas ainda não reproduz vídeo. A Parte 5 pode adicionar player HLS/ExoPlayer, busca e filtros por categoria.
- Use somente listas e transmissões que você tenha autorização para acessar ou distribuir.
- Em produção, configure HTTPS, segredos fortes, rate limiting, backups e migrações.
