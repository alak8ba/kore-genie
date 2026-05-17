# Étape 06 • Streaming WebSocket

## Objectif

Remplacer la réponse bloquante (on attend 20-30s avant de voir quoi que ce soit) par un streaming token par token : la réponse s'affiche progressivement dès que le LLM commence à générer, comme dans ChatGPT.

---

## Ce qu'on a créé

```
src/main/java/dev/kore/genie/
├── config/
│   ├── OllamaConfig.java       ← ajout du bean StreamingChatLanguageModel
│   └── WebSocketConfig.java    ← enregistrement du handler WebSocket sur /ws/rag
└── streaming/
    └── RagStreamingHandler.java ← handler WebSocket : RAG + stream token par token
```

---

## Pourquoi WebSocket et pas HTTP ?

HTTP classique fonctionne en **requête / réponse** : le client attend que le serveur ait tout calculé avant de recevoir quoi que ce soit. Avec un LLM, ça représente 20 à 60 secondes de blanc.

WebSocket est une **connexion persistante bidirectionnelle** : le serveur peut envoyer des messages au client à tout moment, sans que le client les ait demandés. Parfait pour streamer les tokens un par un.

```
HTTP classique :
[Client]  ──── request ────▶ [Server]
          ◀── response ────  (après 30s)

WebSocket :
[Client]  ──── connect ────▶ [Server]
          ──── question ───▶
          ◀── token 1 ─────
          ◀── token 2 ─────
          ◀── token 3 ─────
          ◀── { done:true }
```

---

## Explications fichier par fichier

### `OllamaConfig.java` - nouveau bean

```java
@Bean
public StreamingChatLanguageModel streamingChatLanguageModel() {
    return OllamaStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(model)
            .timeout(timeout)
            .build();
}
```

`OllamaStreamingChatModel` est la version streaming de `OllamaChatModel`. Au lieu de retourner la réponse complète, il appelle un callback à chaque token généré.

---

### `WebSocketConfig.java`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ragStreamingHandler, "/ws/rag")
                .setAllowedOrigins("*");
    }
}
```

Enregistre le handler WebSocket sur l'URL `/ws/rag`. `setAllowedOrigins("*")` autorise toutes les origines • à restreindre à l'URL du frontend en production.

---

### `RagStreamingHandler.java`

C'est le cœur de cette étape. Il étend `TextWebSocketHandler` • Spring appelle `handleTextMessage` à chaque message reçu du client.

#### Format du message client → serveur

```json
{ "question": "Quelle est notre politique de congés ?" }
```

#### Format des messages serveur → client

Chaque token envoyé :
```json
{ "token": "Selon", "done": false }
{ "token": " les", "done": false }
{ "token": " documents", "done": false }
...
{ "token": "", "done": true }
```

`done: true` signale la fin du stream • le client peut alors afficher la réponse complète ou débloquer l'interface.

#### Le callback `StreamingResponseHandler`

```java
new StreamingResponseHandler<>() {

    @Override
    public void onNext(String token) {
        session.sendMessage(new TextMessage(...));  // un token à la fois
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        session.sendMessage(new TextMessage(...));  // { done: true }
    }

    @Override
    public void onError(Throwable error) {
        session.sendMessage(new TextMessage(...));  // erreur + done: true
    }
}
```

Trois callbacks :
- `onNext` : appelé à chaque token • on l'envoie immédiatement via WebSocket
- `onComplete` : fin normale • on envoie `done: true`
- `onError` : erreur LLM • on envoie le message d'erreur + `done: true`

---

## Tester le WebSocket

### Avec wscat (outil en ligne de commande)

```bash
# Installer wscat
npm install -g wscat

# Se connecter
wscat -c ws://localhost:8080/ws/rag

# Envoyer une question (après connexion)
> {"question": "Quelle est notre politique de congés ?"}

# Réponse attendue (token par token)
< {"token":"Selon","done":false}
< {"token":" les","done":false}
< {"token":" documents","done":false}
...
< {"token":"","done":true}
```

### Avec JavaScript (navigateur)

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/rag');

ws.onopen = () => {
    ws.send(JSON.stringify({ question: "Quelle est notre politique de congés ?" }));
};

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (!data.done) {
        document.getElementById('response').innerText += data.token;
    }
};
```

---

## Récapitulatif des endpoints disponibles

| Endpoint | Type | Usage |
|---|---|---|
| `POST /api/llm/ask` | REST | Appel LLM direct, sans RAG |
| `POST /api/ingest` | REST | Upload et indexation d'un document |
| `POST /api/rag/ask` | REST | RAG complet, réponse bloquante |
| `WS /ws/rag` | WebSocket | RAG complet, streaming token par token |

---

## Concepts clés

| Concept | Explication |
|---|---|
| **WebSocket** | Protocole de communication persistante et bidirectionnelle sur TCP |
| **Streaming LLM** | Le LLM envoie les tokens au fur et à mesure de leur génération |
| **Callback** | Fonction appelée automatiquement quand un événement se produit |
| **`done: true`** | Signal de fin de stream • le client sait que la réponse est complète |
| **`TextWebSocketHandler`** | Classe Spring qui simplifie la gestion des connexions WebSocket textuelles |

---

## Prochaine étape

→ **Étape 07** : Docker Compose complet • Spring Boot rejoint Ollama et Chroma dans un seul `docker compose up`.
