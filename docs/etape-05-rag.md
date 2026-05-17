# Étape 05 • Moteur RAG complet

## Objectif

Assembler la pièce centrale du projet : à chaque question, on cherche les chunks les plus pertinents dans Chroma, on les injecte dans le prompt, et LLaMA 3 génère une réponse ancrée sur les documents de l'entreprise.

---

## Ce qu'on a créé

```
src/main/java/dev/kore/genie/
└── rag/
    ├── RagService.java      ← moteur RAG : retrieval + prompt + LLM
    └── RagController.java   ← POST /api/rag/ask
```

---

## Le flux RAG étape par étape

```
[Question utilisateur]
        |
  1. embed(question)         → vecteur de la question via Ollama
        |
  2. findRelevant()          → TOP_K chunks les plus proches dans Chroma
        |
  3. Construction du prompt  → contexte = chunks concaténés + question
        |
  4. chatLanguageModel.generate()  → LLaMA 3 génère la réponse
        |
  [Réponse + sources]
```

---

## Explications fichier par fichier

### `RagService.java`

#### Étape 1 • Vectoriser la question

```java
Embedding questionEmbedding = embeddingModel.embed(question).content();
```

La question de l'utilisateur est transformée en vecteur, exactement comme les chunks lors de l'ingestion. C'est ce qui permet de comparer sémantiquement la question aux documents.

#### Étape 2 • Rechercher dans Chroma

```java
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
        questionEmbedding, TOP_K, MIN_SCORE
);
```

| Paramètre | Valeur | Signification |
|---|---|---|
| `TOP_K` | 5 | Nombre maximum de chunks à récupérer |
| `MIN_SCORE` | 0.5 | Score de similarité cosinus minimum (0 = rien, 1 = identique) |

Si aucun chunk ne dépasse le score minimum, on retourne un message explicite sans appeler le LLM • pas de réponse inventée.

#### Étape 3 • Construire le prompt enrichi

```java
String context = matches.stream()
        .map(match -> match.embedded().text())
        .collect(Collectors.joining("\n\n---\n\n"));

String userPrompt = """
        Contexte :
        %s

        Question : %s
        """.formatted(context, question);
```

Le prompt envoyé à LLaMA 3 contient deux parties :
- **Le contexte** : les 5 meilleurs chunks séparés par `---`
- **La question** : ce que l'utilisateur a posé

Le LLM ne voit jamais Chroma • il reçoit simplement un texte structuré et doit répondre à partir de lui.

#### Étape 4 • System prompt strict

```java
private static final String SYSTEM_PROMPT = """
        Tu es un assistant IA privé d'entreprise.
        Réponds uniquement à partir du contexte fourni ci-dessous.
        Si la réponse ne se trouve pas dans le contexte, dis clairement que tu ne sais pas.
        Ne fabrique pas d'information. Sois précis et concis.
        """;
```

Ce system prompt est crucial. Sans lui, le LLM répondrait à partir de ses connaissances générales (mémorisation d'entraînement), ce qui est exactement ce qu'on veut éviter. Il doit raisonner **uniquement** sur le contexte fourni.

#### La réponse enrichie

```java
return new RagAnswer(response.content().text(), sources, matches.size());
```

On retourne trois informations :
- `answer` : la réponse générée
- `sources` : la liste des fichiers sources utilisés
- `chunksUsed` : le nombre de chunks qui ont contribué à la réponse

---

## Tester le RAG

```bash
# Prérequis : un document déjà ingéré via POST /api/ingest

curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Quelle est notre politique de congés ?"}'
```

### Réponse attendue

```json
{
  "answer": "Selon les documents internes, la politique de congés prévoit...",
  "sources": ["politique-rh.pdf"],
  "chunksUsed": 3
}
```

---

## Comparaison : /api/llm/ask vs /api/rag/ask

| Endpoint | Contexte | Usage |
|---|---|---|
| `POST /api/llm/ask` | Aucun • LLM seul | Test du LLM, questions générales |
| `POST /api/rag/ask` | Chunks Chroma injectés | Questions sur les documents métier |

---

## Schéma complet du système à ce stade

```
[Document PDF/Word]
      |
  POST /api/ingest
      |
  Tika → chunks → Ollama (embed) → Chroma
                                      |
[Question utilisateur]                |
      |                               |
  POST /api/rag/ask                   |
      |                               |
  Ollama (embed question) ────────────┘
      |
  Chroma (findRelevant TOP_K)
      |
  Prompt = contexte + question
      |
  Ollama LLaMA 3 (generate)
      |
  { answer, sources, chunksUsed }
```

---

## Concepts clés

| Concept | Explication |
|---|---|
| **Similarité cosinus** | Mesure l'angle entre deux vecteurs • 1.0 = identiques, 0.0 = sans rapport |
| **TOP_K** | On ne prend que les K meilleurs chunks • le LLM a une fenêtre de contexte limitée |
| **MIN_SCORE** | Seuil de pertinence • évite d'injecter des chunks hors-sujet |
| **System prompt strict** | Empêche le LLM d'halluciner des réponses hors contexte |
| **Sources** | Traçabilité : on sait quels fichiers ont servi à construire la réponse |
| **Hallucination** | Quand un LLM invente une information plausible mais fausse • le RAG réduit ce risque |

---

## Prochaine étape

→ **Étape 06** : Streaming WebSocket • la réponse s'affiche mot par mot au lieu d'attendre la fin.
