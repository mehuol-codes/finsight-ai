package dev.mehuol.finsight.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import dev.mehuol.finsight.tool.GoldMarketTools;

/** Chat clients and prompts. Prompt texts live in src/main/resources/prompts. */
@Configuration
public class AiConfig {

    /** The conversational assistant: system prompt, market data tools and per-chat memory. */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, GoldMarketTools goldTools,
            @Value("classpath:prompts/chat-system.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultTools(goldTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /** One-shot summaries of a whole document; no memory or tools. */
    @Bean
    public ChatClient summaryClient(ChatClient.Builder builder,
            @Value("classpath:prompts/report-summary.st") Resource summaryPrompt) {
        return builder.defaultSystem(summaryPrompt).build();
    }

    /**
     * Wraps the user's question with retrieved document excerpts. Replaces Spring AI's default
     * RAG prompt, which forbids answering from anything but the documents.
     */
    @Bean
    public PromptTemplate documentContextPrompt(@Value("classpath:prompts/document-context.st") Resource template) {
        return new PromptTemplate(template);
    }
}
