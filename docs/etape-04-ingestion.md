# Étape 04 • Ingestion de documents dans Chroma

## Objectif

Créer le pipeline d'ingestion : un fichier (PDF, Word, Markdown) est uploadé, son texte est extrait par Apache Tika, découpé en chunks, transformé en vecteurs (embeddings), puis stocké dans Chroma DB.

---

## Ce qu'on a créé

```
src/main/java/dev/kore/genie/
├── config/
│   └── ChromaConfig.java          ← beans EmbeddingModel + EmbeddingStore
└── ingestion/
    ├── IngestionService.java       ← pipeline complet : Tika → chunks → Chroma
    └── IngestionController.java    ← POST /api/ingest (multipart/form-data)
```

---

## Le pipeline d'ingestion étape par étape

```
[Fichier uploadé]
      |
  Tika.parse()          → extraction du texte brut (PDF, Word, Markdown...)
      |
  DocumentSplitter      → découpage en chunks de 500 tokens (overlap 50)
      |
  EmbeddingModel        → chaque chunk → vecteur float[] via Ollama
      |
  ChromaEmbeddingStore  → stockage des vecteurs dans Chroma DB
```

---

## Explications fichier par fichier

### `ChromaConfig.java`

```java
@Bean
public EmbeddingModel embeddingModel() {
    return OllamaEmbeddingModel.builder()
            .baseUrl(ollamaBaseUrl)
            .modelName(ollamaModel)
            .build();
}

@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    return ChromaEmbeddingStore.builder()
            .baseUrl(chromaBaseUrl)
            .collectionName(chromaCollection)
            .build();
}
```

Deux beans sont créés :

| Bean | Type | Rôle |
|---|---|---|
| `EmbeddingModel` | `OllamaEmbeddingModel` | Transforme un texte en vecteur via Ollama |
| `EmbeddingStore` | `ChromaEmbeddingStore` | Stocke et recherche des vecteurs dans Chroma |

> On réutilise le même modèle Ollama (LLaMA 3) pour les embeddings. En production on utiliserait un modèle dédié à l'embedding (ex: `nomic-embed-text`), plus léger et plus précis.

---

### `IngestionService.java`

#### Extraction du texte avec Tika

```java
AutoDetectParser parser = new AutoDetectParser();
BodyContentHandler handler = new BodyContentHandler(-1);
parser.parse(stream, handler, metadata);
```

`AutoDetectParser` détecte automatiquement le type du fichier (PDF, DOCX, Markdown, HTML...) et applique le bon parseur. `-1` dans `BodyContentHandler` signifie pas de limite de taille.

#### Découpage en chunks

```java
DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
List<TextSegment> segments = splitter.split(document);
```

| Paramètre | Valeur | Signification |
|---|---|---|
| `CHUNK_SIZE` | 500 | Taille maximale d'un chunk en tokens |
| `CHUNK_OVERLAP` | 50 | Chevauchement entre deux chunks consécutifs |

**Pourquoi chevaucher ?** Un chunk coupé en milieu de phrase perd du sens. Le chevauchement garantit que chaque idée apparaît dans au moins un chunk complet.

#### Embedding et stockage

```java
List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
embeddingStore.addAll(embeddings, segments);
```

`embedAll` envoie tous les chunks à Ollama en une seule passe. Chaque chunk revient sous forme de vecteur flottant. `addAll` les stocke dans Chroma avec le texte original associé.

---

### `IngestionController.java`

```java
@PostMapping
public ResponseEntity<IngestionResponse> ingest(@RequestParam("file") MultipartFile file)
```

`@RequestParam("file")` attend un fichier dans un formulaire multipart. C'est le format standard pour l'upload de fichiers en HTTP.

La réponse indique le nom du fichier, le nombre de chunks créés, et le statut.

---

## Tester l'endpoint

```bash
# Uploader un fichier texte ou PDF
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@/chemin/vers/ton/document.pdf"

# Réponse attendue
# { "filename": "document.pdf", "chunks": 42, "status": "OK" }
```

---

## Vérifier dans Chroma

```bash
# Lister les collections
curl http://localhost:8000/api/v1/collections

# Compter les vecteurs dans la collection
curl http://localhost:8000/api/v1/collections/kore-genie-docs/count
```

---

## Concepts clés

| Concept | Explication |
|---|---|
| **Apache Tika** | Bibliothèque Java qui extrait le texte de tout type de fichier (PDF, Word, HTML, code...) |
| **Chunking** | Découpage du texte en petits morceaux • le LLM a une fenêtre de contexte limitée |
| **Overlap** | Chevauchement entre chunks pour ne pas couper les idées en deux |
| **Embedding** | Transformation d'un texte en vecteur numérique • proche sémantiquement = vecteurs proches |
| **EmbeddingStore** | Base qui stocke les vecteurs et permet de les rechercher par similarité |
| **Multipart** | Format HTTP pour l'upload de fichiers binaires |

---

## Prochaine étape

→ **Étape 05** : Moteur RAG complet • à chaque question, on cherche les chunks pertinents dans Chroma et on les injecte dans le prompt envoyé à LLaMA 3.
