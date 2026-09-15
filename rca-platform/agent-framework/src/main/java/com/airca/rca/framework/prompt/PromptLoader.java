package com.airca.rca.framework.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads prompts from version-controlled files under {@code resources/prompts/} instead
 * of embedding them as Java string literals (spec section 48) — so a prompt change is a
 * text diff, reviewable and versionable independent of code changes, and so the same
 * prompt text can be reused by evaluation tooling.
 */
@Component
public class PromptLoader {

    public String load(String promptName) {
        return load(promptName, Map.of());
    }

    public String load(String promptName, Map<String, String> variables) {
        String template = readClasspathResource("prompts/" + promptName + ".txt");
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String readClasspathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt not found on classpath: " + path, e);
        }
    }
}
