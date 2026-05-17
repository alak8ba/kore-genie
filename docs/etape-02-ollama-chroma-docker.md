# Étape 02 • Ollama + Chroma via Docker Compose

## Objectif

Lancer en local les deux services d'infrastructure dont kore-genie a besoin :
- **Ollama** : le serveur qui fait tourner LLaMA 3
- **Chroma DB** : la base de données vectorielle qui stocke les embeddings

---

## Ce qu'on a créé

```
kore-genie/
└── docker-compose.yml    ← définition des deux services
```

---

## Comprendre le `docker-compose.yml`

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama

  chroma:
    image: chromadb/chroma:latest
    ports:
      - "8000:8000"
    volumes:
      - chroma-data:/chroma/chroma
    environment:
      - IS_PERSISTENT=TRUE
      - ANONYMIZED_TELEMETRY=FALSE
```

### Ligne par ligne

| Clé | Rôle |
|---|---|
| `image` | Image Docker à utiliser (téléchargée depuis Docker Hub) |
| `ports: "11434:11434"` | `hôte:conteneur` • on accède au service via `localhost:11434` |
| `volumes` | Persistance : les données survivent à un redémarrage du conteneur |
| `IS_PERSISTENT=TRUE` | Chroma sauvegarde les vecteurs sur disque (sinon tout est perdu à l'arrêt) |
| `ANONYMIZED_TELEMETRY=FALSE` | Désactive l'envoi de statistiques vers Chroma • zéro donnée sortante |
| `restart: unless-stopped` | Le conteneur redémarre automatiquement sauf si on l'arrête manuellement |

---

## Commandes à exécuter

### 1. Démarrer les services

```bash
docker compose up -d
```

`-d` = detached, les conteneurs tournent en arrière-plan.

### 2. Vérifier qu'ils sont bien lancés

```bash
docker compose ps
```

Tu dois voir deux conteneurs avec le statut `running`.

### 3. Télécharger LLaMA 3

Le modèle ne se télécharge pas automatiquement. Il faut le demander à Ollama :

```bash
docker exec -it kore-ollama ollama pull llama3
```

> Attention : LLaMA 3 (8B) pèse environ **4,7 Go**. Le téléchargement peut prendre plusieurs minutes selon ta connexion. C'est un téléchargement unique • le modèle est ensuite stocké dans le volume `ollama-data`.

### 4. Vérifier qu'Ollama répond

```bash
curl http://localhost:11434/api/tags
```

Tu dois voir la liste des modèles installés, dont `llama3`.

### 5. Test rapide du LLM en ligne de commande

```bash
docker exec -it kore-ollama ollama run llama3 "Dis bonjour en français"
```

Si tu obtiens une réponse, **Ollama fonctionne correctement**.

### 6. Vérifier que Chroma répond

```bash
curl http://localhost:8000/api/v1/heartbeat
```

Réponse attendue : `{"nanosecond heartbeat": ...}`

---

## Schéma de communication

```
[Spring Boot :8080]
        |
        |── HTTP ──▶ [Ollama :11434]  ← LLaMA 3 tourne ici
        |
        └── HTTP ──▶ [Chroma :8000]   ← vecteurs stockés ici
```

Spring Boot parle à Ollama et Chroma via HTTP en local. Aucun appel externe.

---

## Commandes utiles

```bash
# Arrêter les services (sans supprimer les données)
docker compose stop

# Redémarrer
docker compose start

# Voir les logs d'Ollama
docker compose logs -f ollama

# Voir les logs de Chroma
docker compose logs -f chroma

# Lister les modèles installés dans Ollama
docker exec kore-ollama ollama list

# Tout supprimer (conteneurs + volumes = données perdues)
docker compose down -v
```

---

## Concepts clés

| Concept | Explication |
|---|---|
| **Docker Compose** | Outil pour définir et lancer plusieurs conteneurs ensemble via un seul fichier YAML |
| **Volume Docker** | Espace de stockage persistant géré par Docker • les données survivent aux redémarrages |
| **Ollama** | Serveur REST qui charge un modèle LLM en mémoire et expose une API d'inférence |
| **LLaMA 3** | Modèle de langage open source de Meta • 8 milliards de paramètres, tourne sans GPU dédié |
| **Chroma DB** | Base vectorielle légère, open source, stocke et recherche des embeddings |

---

## Prochaine étape

→ **Étape 03** : Premier appel au LLM depuis Java via LangChain4j • Spring Boot parle à Ollama.
