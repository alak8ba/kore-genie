# Étape 01 • Scaffolding du projet Spring Boot 3

## Objectif

Créer le squelette Maven de kore-genie : point d'entrée Spring Boot, configuration, et dépendances de toute la stack.

---

## Ce qu'on a créé

```
kore-genie/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/dev/kore/genie/
│   │   │   └── KoreGenieApplication.java   ← point d'entrée Spring Boot
│   │   └── resources/
│   │       └── application.yml              ← configuration centralisée
│   └── test/
│       └── java/dev/kore/genie/
│           └── KoreGenieApplicationTest.java
└── docs/
    └── etape-01-scaffolding.md              ← ce fichier
```

---

## Explications fichier par fichier

### `pom.xml`

Le `pom.xml` est le fichier de configuration Maven. Il déclare :

- **Le parent Spring Boot 3.2.5** : il gère toutes les versions des dépendances Spring automatiquement.
- **Java 21** : on utilise la dernière LTS pour les Virtual Threads (Project Loom).
- **Les dépendances** :

| Dépendance | Rôle |
|---|---|
| `spring-boot-starter-web` | API REST (HTTP) |
| `spring-boot-starter-websocket` | Streaming temps réel |
| `langchain4j` | Orchestration IA (core) |
| `langchain4j-ollama` | Connecteur vers Ollama / LLaMA 3 |
| `langchain4j-chroma` | Connecteur vers Chroma DB |
| `tika-core` + `tika-parsers-standard-package` | Extraction de texte (PDF, Word, etc.) |
| `lombok` | Réduction du boilerplate Java |

---

### `KoreGenieApplication.java`

```java
@SpringBootApplication
public class KoreGenieApplication {
    public static void main(String[] args) {
        SpringApplication.run(KoreGenieApplication.class, args);
    }
}
```

`@SpringBootApplication` est une méta-annotation qui active trois choses :
- `@Configuration` : cette classe peut déclarer des beans Spring
- `@EnableAutoConfiguration` : Spring configure automatiquement ce qu'il détecte (ex: Jackson, Tomcat)
- `@ComponentScan` : Spring scanne le package `io.kore.genie` et tous ses sous-packages pour trouver les composants (`@Service`, `@Controller`, etc.)

---

### `application.yml`

C'est la configuration centralisée de l'application. On a défini nos propres propriétés sous le préfixe `kore.genie` :

```yaml
kore:
  genie:
    ollama:
      base-url: http://localhost:11434   # adresse du serveur Ollama
      model: llama3                      # modèle à utiliser
    chroma:
      base-url: http://localhost:8000    # adresse de Chroma DB
      collection: kore-genie-docs        # collection de vecteurs
    ingestion:
      upload-dir: ./uploads              # dossier de dépôt des documents
```

Ces valeurs seront injectées dans les beans Spring via `@ConfigurationProperties`.

> **Pourquoi YAML et pas .properties ?**
> YAML permet une hiérarchie lisible. Avec `.properties` on écrirait `kore.genie.ollama.base-url=...` sur chaque ligne • moins lisible dès qu'on a plusieurs niveaux.

---

## Concepts clés à retenir

| Concept | Explication rapide |
|---|---|
| **Maven** | Outil de build Java. `pom.xml` = recette du projet |
| **Spring Boot** | Framework qui démarre un serveur Tomcat embarqué et autoconfigure tout |
| **`@SpringBootApplication`** | Point d'entrée, active le scan et l'autoconfiguration |
| **`application.yml`** | Fichier de config central, lu automatiquement par Spring au démarrage |
| **LangChain4j** | Bibliothèque Java pour orchestrer des LLMs (comme LangChain en Python) |
| **Ollama** | Serveur local qui fait tourner LLaMA 3 • on l'installera via Docker |
| **Chroma DB** | Base de données vectorielle • stocke les embeddings des documents |

---

## Prochaine étape

→ **Étape 02** : Lancer Ollama + LLaMA 3 via Docker Compose et vérifier qu'il répond.
