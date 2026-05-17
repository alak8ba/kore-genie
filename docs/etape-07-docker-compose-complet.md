# Étape 07 • Docker Compose complet

## Objectif

Conteneuriser Spring Boot et l'ajouter au Docker Compose. Un seul `docker compose up` démarre l'infrastructure complète : Ollama, Chroma et l'API kore-genie.

---

## Ce qu'on a créé / modifié

```
kore-genie/
├── Dockerfile                          ← build multi-étapes Spring Boot
├── .dockerignore                       ← exclusions du contexte Docker
├── docker-compose.yml                  ← ajout service kore-genie + healthchecks
└── src/main/resources/application.yml ← variables d'environnement avec fallback
```

---

## Explications fichier par fichier

### `Dockerfile` • build multi-étapes

```dockerfile
# Étape 1 : build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Étape 2 : image finale légère
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Pourquoi deux étapes ?**

| Étape | Image | Contenu | Taille |
|---|---|---|---|
| `build` | `maven:3.9-eclipse-temurin-21` | JDK + Maven + sources | ~600 Mo |
| finale | `eclipse-temurin:21-jre` | JRE + JAR uniquement | ~200 Mo |

L'image finale ne contient pas Maven, les sources, ni les dépendances Maven en cache. Elle est 3x plus légère et ne contient rien d'inutile.

`RUN mvn dependency:go-offline` en premier : si le `pom.xml` n'a pas changé, Docker réutilise le cache de cette couche. Le téléchargement des dépendances n'est refait que si le `pom.xml` change.

---

### `docker-compose.yml` • ajout de kore-genie + healthchecks

```yaml
kore-genie:
  build:
    context: .
    dockerfile: Dockerfile
  environment:
    - KORE_GENIE_OLLAMA_BASE_URL=http://ollama:11434
    - KORE_GENIE_CHROMA_BASE_URL=http://chroma:8000
  depends_on:
    ollama:
      condition: service_healthy
    chroma:
      condition: service_healthy
```

Points importants :

| Clé | Explication |
|---|---|
| `build: context: .` | Docker construit l'image depuis le `Dockerfile` local |
| `http://ollama:11434` | Dans Docker Compose, les services se parlent par leur **nom de service**, pas par `localhost` |
| `depends_on: condition: service_healthy` | kore-genie ne démarre qu'une fois Ollama et Chroma répondent au healthcheck |
| `volumes: uploads-data` | Les fichiers uploadés persistent entre les redémarrages |

**Pourquoi `http://ollama:11434` et non `http://localhost:11434` ?**
Dans Docker Compose, chaque conteneur a son propre réseau. `localhost` dans kore-genie pointe vers kore-genie lui-même, pas vers Ollama. Les services se référencent par leur nom défini dans `docker-compose.yml`.

---

### `application.yml` • variables d'environnement avec fallback

```yaml
kore:
  genie:
    ollama:
      base-url: ${KORE_GENIE_OLLAMA_BASE_URL:http://localhost:11434}
      model: ${KORE_GENIE_OLLAMA_MODEL:llama3}
```

La syntaxe `${VAR:valeur_defaut}` permet deux modes :

| Mode | Valeur utilisée |
|---|---|
| `mvn spring-boot:run` en local | `http://localhost:11434` (fallback) |
| `docker compose up` | `http://ollama:11434` (variable injectée par Docker) |

Un seul fichier de config pour les deux environnements.

---

## Architecture finale des conteneurs

```
┌─────────────────────────────────────────────────┐
│               Docker Compose network             │
│                                                  │
│  ┌────────────┐    ┌────────────┐               │
│  │   ollama   │    │   chroma   │               │
│  │  :11434    │    │   :8000    │               │
│  └─────┬──────┘    └──────┬─────┘               │
│        │                  │                      │
│        └────────┬─────────┘                      │
│                 │                                │
│          ┌──────┴──────┐                         │
│          │ kore-genie  │                         │
│          │    :8080    │                         │
│          └──────┬──────┘                         │
└─────────────────┼───────────────────────────────┘
                  │
            localhost:8080
                  │
            [Utilisateur]
```

---

## Commandes

```bash
# Construire et démarrer tout le stack
docker compose up --build -d

# Suivre les logs de kore-genie au démarrage
docker compose logs -f kore-genie

# Vérifier que tout tourne
docker compose ps

# Tester l'API depuis l'hôte
curl -X POST http://localhost:8080/api/llm/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Test de bout en bout"}'

# Reconstruire uniquement kore-genie (après modif du code)
docker compose build kore-genie
docker compose up -d kore-genie

# Arrêter tout sans perdre les données
docker compose stop

# Tout supprimer, données comprises
docker compose down -v
```

---

## Concepts clés

| Concept | Explication |
|---|---|
| **Multi-stage build** | Dockerfile en deux étapes • l'image finale ne contient que le strict nécessaire |
| **Cache Docker** | Docker réutilise les couches non modifiées • copier `pom.xml` avant les sources optimise le cache |
| **Réseau Compose** | Les services se voient par leur nom • `ollama`, `chroma`, `kore-genie` |
| **`depends_on` + healthcheck** | Garantit l'ordre de démarrage et attend que les services soient vraiment prêts |
| **Variable d'env avec fallback** | `${VAR:default}` • un seul `application.yml` pour le dev local et Docker |

---

## Prochaine étape

→ **Étape 08** • Frontend Angular minimal • une interface chat qui consomme le WebSocket de streaming.
