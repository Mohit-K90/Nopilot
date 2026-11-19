package com.nopilot.aiservice;

import com.nopilot.aiservice.config.AiProperties;
import com.nopilot.aiservice.service.ChunkingService;
import com.nopilot.aiservice.service.RequestPolicy;
import com.nopilot.aiservice.util.FileHasher;
import com.nopilot.aiservice.util.LanguageDetector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCoreTests {
    @Test void sha256IsStableAndUsesTwoHexCharactersPerByte() {
        String hash=FileHasher.sha256Hex("hello");
        assertEquals(64,hash.length());
        assertEquals(hash,FileHasher.sha256Hex("hello"));
        assertNotEquals(hash,FileHasher.sha256Hex("Hello"));
    }
    @Test void languageDetectorRecognizesSupportedFiles() {
        assertEquals(LanguageDetector.JAVA,LanguageDetector.detect("src/Main.java"));
        assertEquals(LanguageDetector.PYTHON,LanguageDetector.detect("main.py"));
        assertEquals(LanguageDetector.DOCUMENTATION,LanguageDetector.detect("README.md"));
        assertNull(LanguageDetector.detect("image.png"));
    }
    @Test void requestPolicyBlocksCodeGenerationButAllowsExplanations() {
        RequestPolicy policy=new RequestPolicy();
        assertTrue(policy.isCodeChangeRequest("Please implement a cache"));
        assertTrue(policy.isCodeChangeRequest("write a controller"));
        assertFalse(policy.isCodeChangeRequest("How is the controller implemented?"));
    }
    @Test void chunkingReturnsLineRangesAndContent() {
        ChunkingService chunker=new ChunkingService(new AiProperties());
        var chunks=chunker.chunk("src/Main.java",LanguageDetector.JAVA,
                "public class Main {\n  public static void main(String[] args) {\n    System.out.println(1);\n  }\n}\n");
        assertFalse(chunks.isEmpty());
        assertEquals(1,chunks.get(0).startLine());
        assertTrue(chunks.get(0).content().contains("class Main"));
        assertEquals(64,chunks.get(0).contentHash().length());
    }
}
