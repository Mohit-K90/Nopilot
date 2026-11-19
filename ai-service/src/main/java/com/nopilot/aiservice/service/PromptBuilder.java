package com.nopilot.aiservice.service;  import com.nopilot.aiservice.model.ContextChunk;  import org.springframework.stereotype.Component;  import java.util.List;  /**  * Builds the prompts. Kept short on purpose: every word here is sent with every request.  * No conversation history is ever included.  */ @Component public class PromptBuilder {      public static final String INSUFFICIENT_CONTEXT =             "The indexed code does not contain enough information to answer this question.";      private static final String CHAT_SYSTEM =
            "You are NoPilot, an assistant that explains code. Use ONLY the numbered code context.\\n"
            + "Rules:\\n"
            + "- Cite sources as [n] and name the file path.\\n"
            + "- If the context is not enough, say: \"" + INSUFFICIENT_CONTEXT + "\" and say what is missing.\\n"
            + "- Never invent files, classes, methods or behaviour.\\n"
            + "- Explain; do not write new code or suggest edits.\\n"
            + "- Be concise.";

    private static final String GUIDE_SYSTEM =
            "You are NoPilot, an assistant that writes onboarding notes for unfamiliar codebases.\\n"
            + "Use ONLY the repository facts and numbered code context provided. Cite sources as [n].\\n"
            + "If something is not supported by them, write \"not determined from the indexed code\".\\n"
            + "Never invent files, classes or behaviour. Do not write or suggest code changes.\\n"
            + "Write exactly three markdown sections: \"## Purpose\", \"## Main execution flow\", "
            + "and \"## How the components interact\". Be concise.";

    public String chatSystemPrompt() {         return CHAT_SYSTEM;     }      public String guideSystemPrompt() {         return GUIDE_SYSTEM;     }      public String chatUserPrompt(List<ContextChunk> context, String question) {         return "Code context:\n" + formatContext(context) + "\nQuestion: " + question;     }      public String guideUserPrompt(String factsSummary, List<ContextChunk> context) {         return "Repository facts:\n" + factsSummary + "\nCode context:\n" + formatContext(context)                 + "\nWrite the three sections now.";     }      String formatContext(List<ContextChunk> context) {         StringBuilder out = new StringBuilder();         for (ContextChunk chunk : context) {             out.append('[').append(chunk.number()).append("] ")                     .append(chunk.filePath())                     .append(" (").append(chunk.language())                     .append(", lines ").append(chunk.startLine()).append('-').append(chunk.endLine());             if (chunk.symbol() != null) {                 out.append(", ").append(chunk.symbol());             }             out.append(")\n```\n").append(chunk.text()).append("\n```\n");         }         return out.toString();     } }
