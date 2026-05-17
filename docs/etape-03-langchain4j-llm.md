# Étape 03 • Premier appel LLM via LangChain4j

## Objectif

Faire parler Spring Boot à Ollama depuis Java. À la fin de cette étape, on peut envoyer une question via HTTP et recevoir une réponse de LLaMA 3.

---

## Ce qu'on a créé

```
src/main/java/dev/kore/genie/
├── config/
│   └── OllamaConfig.java      ← crée le bean ChatLanguageModel
└── llm/
    ├── LlmService.java         ← logique d'appel au LLM
    └── LlmController.java      ← endpoint REST POST /api/llm/ask
```

---

## Explications fichier par fichier

### `OllamaConfig.java`

```java
@Configuration
public class OllamaConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)     // http://localhost:11434
                .modelName(model)     // llama3
                .timeout(timeout)     // 120s
                .build();
    }
}
```

**Rôle :** Cette classe crée un **bean Spring** de type `ChatLanguageModel`.

Un bean = un objet géré par Spring. Une fois déclaré ici avec `@Bean`, il peut être injecté partout dans l'application via `@RequiredArgsConstructor` ou `@Autowired`.

`@Value("${kore.genie.ollama.base-url}")` lit la valeur depuis `application.yml` • on ne met jamais d'URL en dur dans le code.

`OllamaChatModel` est la classe LangChain4j qui sait parler au format API d'Ollama. Elle s'occupe de la sérialisation HTTP, des retries, des timeouts.

---

### `LlmService.java`

```java
@Service
@RequiredArgsConstructor
public class LlmService {

    private final ChatLanguageModel chatLanguageModel;

    public String ask(String question) {
        Response<AiMessage> response = chatLanguageModel.generate(
                List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(question)
                )
        );
        return response.content().text();
    }
}
```

**Rôle :** Contient la logique métier de l'appel au LLM.

On envoie **deux messages** à chaque appel :

| Message | Rôle |
|---|---|
| `SystemMessage` | Instructions permanentes données au LLM (son comportement, ses limites) |
| `UserMessage` | La question de l'utilisateur |

C'est exactement comme l'interface ChatGPT : il y a un "system prompt" qui cadre le LLM, puis la question de l'utilisateur.

Le `SYSTEM_PROMPT` définit ici que le LLM doit se comporter comme un assistant IA privé d'entreprise et ne répondre qu'à partir du contexte fourni • fondamental pour le RAG qu'on construira ensuite.

---

### `LlmController.java`

```java
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> ask(@RequestBody QuestionRequest request) {
        String answer = llmService.ask(request.question());
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    public record QuestionRequest(String question) {}
    public record AnswerResponse(String answer) {}
}
```

**Rôle :** Expose un endpoint HTTP POST.

`record` = classe Java 16+ immuable, idéale pour les DTOs (objets de transfert de données). Pas besoin de getters/setters/constructeurs • Java les génère automatiquement.

---

## Tester l'endpoint

### Prérequis

- Docker Compose lancé (`docker compose up -d`)
- LLaMA 3 téléchargé (`docker exec -it kore-ollama ollama pull llama3`)
- Spring Boot démarré (`mvn spring-boot:run`)

### Appel avec curl

```bash
curl -X POST http://localhost:8080/api/llm/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Explique ce qu'\''est le RAG en 3 phrases"}'
```

### Réponse attendue

```json
{
  "answer": "Le RAG (Retrieval Augmented Generation) est une technique qui combine..."
}
```

---

## Flux complet de cette étape

```
[curl POST /api/llm/ask]
        |
  LlmController.ask()
        |
  LlmService.ask()
        |
  OllamaChatModel.generate()   ← LangChain4j
        |
  HTTP POST localhost:11434     ← Ollama dans Docker
        |
  LLaMA 3 génère la réponse
        |
  ← réponse JSON remonte la chaîne
```

---

## Concepts clés

| Concept | Explication |
|---|---|
| **Bean Spring** | Objet instancié et géré par le conteneur Spring • injecté automatiquement là où c'est déclaré |
| **`@Configuration`** | Classe qui déclare des beans via des méthodes `@Bean` |
| **`@Service`** | Composant Spring contenant la logique métier |
| **`@RestController`** | Composant Spring qui gère les requêtes HTTP et retourne du JSON |
| **`@Value`** | Injecte une valeur depuis `application.yml` |
| **System prompt** | Instructions données au LLM pour cadrer son comportement |
| **`ChatLanguageModel`** | Interface LangChain4j abstraite • fonctionne avec Ollama, OpenAI, etc. |
| **Java record** | Type de classe immuable, idéal pour les DTOs |

---

## Prochaine étape

→ **Étape 04** : Lancer Chroma DB et y stocker les premiers vecteurs • préparation du RAG.
