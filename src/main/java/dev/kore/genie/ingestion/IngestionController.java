package dev.kore.genie.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping
    public ResponseEntity<IngestionResponse> ingest(@RequestParam("file") MultipartFile file) {
        try {
            int chunks = ingestionService.ingest(file);
            return ResponseEntity.ok(new IngestionResponse(
                    file.getOriginalFilename(),
                    chunks,
                    "OK"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new IngestionResponse(
                    file.getOriginalFilename(),
                    0,
                    "ERREUR : " + e.getMessage()
            ));
        }
    }

    public record IngestionResponse(String filename, int chunks, String status) {}
}
