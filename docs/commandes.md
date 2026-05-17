# kore-genie — Commandes techniques

Référence centralisée de toutes les commandes du projet, étape par étape.

---

## Étape 01 — Build Maven

```bash
# Compiler le projet
mvn clean compile

# Lancer les tests
mvn test

# Packager en JAR
mvn clean package

# Lancer l'application
mvn spring-boot:run
```

---

## Étape 02 — Docker Compose (Ollama + Chroma)

```bash
# Démarrer les services en arrière-plan
docker compose up -d

# Vérifier que les conteneurs tournent
docker compose ps

# Télécharger LLaMA 3 dans Ollama (une seule fois, ~4.7 Go)
docker exec -it kore-ollama ollama pull llama3

# Lister les modèles installés
docker exec kore-ollama ollama list

# Test rapide du LLM en ligne de commande
docker exec -it kore-ollama ollama run llama3 "Dis bonjour en français"

# Vérifier qu'Ollama répond via HTTP
curl http://localhost:11434/api/tags

# Vérifier que Chroma répond via HTTP
curl http://localhost:8000/api/v1/heartbeat

# Voir les logs en temps réel
docker compose logs -f ollama
docker compose logs -f chroma

# Arrêter les services (données conservées)
docker compose stop

# Redémarrer les services
docker compose start

# Tout supprimer, données comprises (destructif)
docker compose down -v
```

---

## Étape 03 — Premier appel LLM

```bash
# Prérequis : Docker Compose up + llama3 téléchargé + mvn spring-boot:run

# Poser une question au LLM via l'API REST
curl -X POST http://localhost:8080/api/llm/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Explique ce qu'\''est le RAG en 3 phrases"}'

# Réponse attendue
# { "answer": "..." }
```

---

## Référence ports

| Service | Port | URL |
|---|---|---|
| Spring Boot | 8080 | http://localhost:8080 |
| Ollama | 11434 | http://localhost:11434 |
| Chroma DB | 8000 | http://localhost:8000 |

---

## Référence conteneurs Docker

| Conteneur | Image | Rôle |
|---|---|---|
| `kore-ollama` | `ollama/ollama` | Serveur LLM local |
| `kore-chroma` | `chromadb/chroma` | Base vectorielle |

---

## Démarrage complet (ordre à respecter)

```bash
# 1. Lancer l'infrastructure
docker compose up -d

# 2. Vérifier que tout tourne
docker compose ps

# 3. Lancer Spring Boot
mvn spring-boot:run

# 4. Tester
curl http://localhost:8080/api/llm/ask \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"question": "Test"}'
```

---

*Ce fichier est mis à jour à chaque nouvelle étape.*
