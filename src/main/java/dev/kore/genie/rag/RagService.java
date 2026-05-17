package dev.kore.genie.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private static final int    TOP_K     = 5;    // nombre de chunks à récupérer
    private static final double MIN_SCORE = 0.5;  // score de similarité minimum

    private static final String SYSTEM_PROMPT = """
            Tu es un assistant IA privé d'entreprise.
            Réponds uniquement à partir du contexte fourni ci-dessous.
            Si la réponse ne se trouve pas dans le contexte, dis clairement que tu ne sais pas.
            Ne fabrique pas d'information. Sois précis et concis.
            """;

    public RagAnswer ask(String question) {
        log.info("Question RAG : {}", question);

        // 1. Transformer la question en vecteur
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. Rechercher les chunks les plus proches dans Chroma
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                questionEmbedding, TOP_K, MIN_SCORE
        );
        log.info("Chunks trouvés : {}", matches.size());

        if (matches.isEmpty()) {
            return new RagAnswer(
                    "Aucun document pertinent trouvé pour répondre à cette question.",
                    List.of(),
                    0
            );
        }

        // 3. Construire le contexte à partir des chunks récupérés
        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        // 4. Construire le prompt enrichi
        String userPrompt = """
                Contexte :
                %s

                Question : %s
                """.formatted(context, question);

        // 5. Envoyer à LLaMA 3 et récupérer la réponse
        Response<AiMessage> response = chatLanguageModel.generate(
                List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
        );

        List<String> sources = matches.stream()
                .map(m -> m.embedded().metadata().getString("filename"))
                .distinct()
                .toList();

        log.info("Réponse générée depuis {} sources", sources.size());

        return new RagAnswer(response.content().text(), sources, matches.size());
    }

    public record RagAnswer(String answer, List<String> sources, int chunksUsed) {}
}
