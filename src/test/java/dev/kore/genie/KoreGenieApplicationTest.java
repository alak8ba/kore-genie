package dev.kore.genie;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class KoreGenieApplicationTest {

    // Mocks des beans qui appellent des services externes au démarrage
    @MockBean ChatLanguageModel chatLanguageModel;
    @MockBean StreamingChatLanguageModel streamingChatLanguageModel;
    @MockBean EmbeddingModel embeddingModel;
    @MockBean EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre sans erreur
    }
}
