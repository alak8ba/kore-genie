package dev.kore.genie.ingestion;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private static final int CHUNK_SIZE    = 500;  // tokens par chunk
    private static final int CHUNK_OVERLAP = 50;   // tokens de chevauchement

    public int ingest(MultipartFile file) throws Exception {
        log.info("Ingestion du fichier : {}", file.getOriginalFilename());

        String text = extractText(file);
        log.info("Texte extrait : {} caractères", text.length());

        Document document = Document.from(text, Metadata.from("filename", file.getOriginalFilename()));

        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        List<TextSegment> segments = splitter.split(document);
        log.info("Découpage : {} chunks", segments.size());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        log.info("Indexation terminée : {} vecteurs stockés dans Chroma", segments.size());
        return segments.size();
    }

    private String extractText(MultipartFile file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 = pas de limite de taille
        org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());

        try (InputStream stream = file.getInputStream()) {
            parser.parse(stream, handler, metadata);
        }
        return handler.toString();
    }
}
