package identity.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {
    @GetMapping("/docs")
    public ResponseEntity<Resource> docs() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("docs/index.html"));
    }
    @GetMapping("/v1/docs/openapi")
    public ResponseEntity<Resource> openapi() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("docs/openapi.json"));
    }
}
