package dev.kore.genie.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/ask")
    public ResponseEntity<RagService.RagAnswer> ask(@RequestBody RagRequest request) {
        return ResponseEntity.ok(ragService.ask(request.question()));
    }

    public record RagRequest(String question) {}
}
