package com.sep490.slms2026.service;

import java.util.Map;

public interface GeminiParserService {
    public Map<String, Object> parseUserPrompt(String userPrompt);
}
