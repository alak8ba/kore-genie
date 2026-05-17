package dev.kore.genie.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagStreamingHandler extends TextWebSocketHandler {

    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectMapper objectMapper;

    private static final int    TOP_K     = 5;
    private static final double MIN_SCORE = 0.5;

    private static final String SYSTEM_PROMPT = """
            Tu es un assistant IA privé d'entreprise.
            Réponds uniquement à partir du contexte fourni.
            Si la réponse n'est pas dans le contexte, dis-le clairement.
            Ne fabrique pas d'information.
            """;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String question = objectMapper.readTree(message.getPayload()).get("question").asText();
        log.info("WebSocket RAG stream - question : {}", question);

        // 1. Vectoriser la question
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. Récupérer les chunks pertinents
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                questionEmbedding, TOP_K, MIN_SCORE
        );

        if (matches.isEmpty()) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(new StreamToken("Aucun document pertinent trouvé.", false))
            ));
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(new StreamToken("", true))
            ));
            return;
        }

        // 3. Construire le prompt enrichi
        String context = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String userPrompt = """
                Contexte :
                %s

                Question : %s
                """.formatted(context, question);

        // 4. Streamer la réponse token par token via WebSocket
        streamingChatLanguageModel.generate(
                List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                ),
                new StreamingResponseHandler<>() {

                    @Override
                    public void onNext(String token) {
                        try {
                            session.sendMessage(new TextMessage(
                                    objectMapper.writeValueAsString(new StreamToken(token, false))
                            ));
                        } catch (Exception e) {
                            log.error("Erreur envoi token WebSocket", e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            session.sendMessage(new TextMessage(
                                    objectMapper.writeValueAsString(new StreamToken("", true))
                            ));
                            log.info("Stream terminé");
                        } catch (Exception e) {
                            log.error("Erreur fin de stream WebSocket", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Erreur LLM stream", error);
                        try {
                            session.sendMessage(new TextMessage(
                                    objectMapper.writeValueAsString(new StreamToken("ERREUR : " + error.getMessage(), true))
                            ));
                        } catch (Exception e) {
                            log.error("Erreur envoi erreur WebSocket", e);
                        }
                    }
                }
        );
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket fermé : {}", status);
    }

    public record StreamToken(String token, boolean done) {}
}
