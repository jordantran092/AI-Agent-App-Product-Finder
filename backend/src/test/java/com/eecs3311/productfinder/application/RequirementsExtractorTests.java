package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.chat.ChatMessage;
import com.eecs3311.productfinder.domain.chat.MessageRole;
import com.eecs3311.productfinder.domain.chat.RequirementsValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementsExtractorTests {

    private static final String READY_RESPONSE = """
            {
              "status":"READY",
              "category":"smartphone",
              "maxPrice":900,
              "mustHaves":["long battery life"],
              "niceToHaves":[],
              "excludedBrands":["Example"],
              "condition":"NEW",
              "attributes":{"storageGb":128}
            }
            """;

    private final ProductSpecSchemaRegistry schemaRegistry =
            new ProductSpecSchemaRegistry(new ObjectMapper());

    @Test
    void extractsAndValidatesSharedAndCategorySpecificRequirements() {
        LLMClient llmClient = clientReturning(READY_RESPONSE);
        RequirementsExtractor extractor = extractor(llmClient);

        RequirementsExtractionResult result = extractor.extract(history("I need a phone with 128 GB storage."), 
                ProfileSettings.unrestricted());

        assertThat(result.isReady()).isTrue();
        assertThat(result.requirements().category()).isEqualTo(ProductCategory.SMARTPHONE);
        assertThat(result.requirements().maxPrice()).isEqualByComparingTo("900");
        assertThat(result.requirements().attributes()).containsEntry("storageGb", 128);
        assertThat(result.requirements().mustHaves()).containsExactly("long battery life");
    }

    @Test
    void asksFollowUpWhenTheCategoryOrRequiredDetailsAreMissing() {
        RequirementsExtractor extractor = extractor(clientReturning("""
                {"status":"FOLLOW_UP","followUpQuestion":"What type of product are you looking for?"}
                """));

        RequirementsExtractionResult result = extractor.extract(history("I need something good."), 
                ProfileSettings.unrestricted());

        assertThat(result.isReady()).isFalse();
        assertThat(result.followUpQuestion()).contains("What type");
    }

    @Test
    void rejectsCategoryAttributesThatDoNotConformToTheSelectedSchema() {
        RequirementsExtractor extractor = extractor(clientReturning(READY_RESPONSE.replace(
                "\"storageGb\":128", "\"storageGb\":-1")));

        assertThatThrownBy(() -> extractor.extract(history("I need a phone."), ProfileSettings.unrestricted()))
                .isInstanceOf(RequirementsValidationException.class)
                .hasMessageContaining("storageGb");
    }

    @Test
    void sendsAllCategorySchemasAndTheSavedProfileAsExtractionContext() {
        LLMClient llmClient = clientReturning("""
                {"status":"FOLLOW_UP","followUpQuestion":"Which product category?"}
                """);
        RequirementsExtractor extractor = extractor(llmClient);

        extractor.extract(history("I need something."), new ProfileSettings(
                java.math.BigDecimal.valueOf(500),
                java.util.Set.of("example"),
                java.util.Set.of(com.eecs3311.productfinder.domain.catalog.ProductCondition.NEW)
        ));

        var request = org.mockito.ArgumentCaptor.forClass(LLMRequest.class);
        org.mockito.Mockito.verify(llmClient).complete(request.capture());
        LLMMessage systemMessage = request.getValue().messages().getFirst();
        assertThat(systemMessage.content()).contains("smartphone", "footwear", "budget 500", "excluded brands");
        assertThat(request.getValue().outputSchema().jsonSchema()).contains("FOLLOW_UP", "READY");
    }

    private RequirementsExtractor extractor(LLMClient llmClient) {
        ObjectMapper mapper = new ObjectMapper();
        return new RequirementsExtractor(
                llmClient,
                new com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder(schemaRegistry),
                schemaRegistry,
                mapper
        );
    }

    private LLMClient clientReturning(String content) {
        LLMClient client = mock(LLMClient.class);
        when(client.complete(any(LLMRequest.class)))
                .thenReturn(new LLMResponse(content, "test-model", "stop", null));
        return client;
    }

    private List<ChatMessage> history(String message) {
        return List.of(new ChatMessage(MessageRole.USER, message, OffsetDateTime.now()));
    }
}
