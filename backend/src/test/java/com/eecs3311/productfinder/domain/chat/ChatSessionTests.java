package com.eecs3311.productfinder.domain.chat;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ChatSessionTests {

    @Test
    void startsInGatheringAndRequiresRequirementsBeforeConfirmation() {
        ChatSession session = ChatSession.start(7);

        assertThat(session.getState()).isEqualTo(ChatState.GATHERING);
        assertThatIllegalStateException()
                .isThrownBy(() -> session.transitionTo(ChatState.CONFIRMING))
                .withMessageContaining("Requirements must be extracted");
    }

    @Test
    void requiresConfirmationBeforeSearchAndAllowsPresentationAfterSearch() {
        ChatSession session = ChatSession.start(7);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);

        assertThatIllegalStateException()
                .isThrownBy(() -> session.transitionTo(ChatState.PRESENTING));

        session.transitionTo(ChatState.SEARCHING);
        session.setLastMatch(new LastMatch(1, 2, "Matches the requested specifications."));
        session.transitionTo(ChatState.PRESENTING);

        assertThat(session.getState()).isEqualTo(ChatState.PRESENTING);
    }

    @Test
    void rejectsTransitionsThatSkipTheWorkflow() {
        ChatSession session = ChatSession.start(7);

        assertThatIllegalStateException()
                .isThrownBy(() -> session.transitionTo(ChatState.SEARCHING))
                .withMessageContaining("Cannot transition chat");
    }

    @Test
    void returningToGatheringClearsPendingRequirements() {
        ChatSession session = ChatSession.start(7);
        session.setPendingRequirements(requirements());
        session.transitionTo(ChatState.CONFIRMING);
        session.transitionTo(ChatState.GATHERING);

        assertThat(session.getPendingRequirements()).isNull();
        assertThat(session.getState()).isEqualTo(ChatState.GATHERING);
    }

    @Test
    void requirementsRejectNegativePriceAndCopyLists() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ChatSession.start(7).setPendingRequirements(
                        new Requirements(ProductCategory.LAPTOP, new BigDecimal("-1"), List.of(), List.of(),
                                List.of(), null)))
                .withMessageContaining("maxPrice must not be negative");

        List<String> mustHaves = new ArrayList<>(List.of("16 GB memory"));
        Requirements requirements = new Requirements(ProductCategory.LAPTOP, null, mustHaves, List.of(),
                List.of(), null);
        mustHaves.add("1 TB storage");

        assertThat(requirements.mustHaves()).containsExactly("16 GB memory");
    }

    @Test
    void requirementsDefensivelyCopyCategoryAttributes() {
        List<Object> connectivity = new ArrayList<>(List.of("5G"));
        Requirements requirements = new Requirements(
                ProductCategory.SMARTPHONE,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of("connectivity", connectivity)
        );
        connectivity.add("Wi-Fi");

        assertThat(requirements.attributes().get("connectivity")).isEqualTo(List.of("5G"));
        assertThat(requirements.attributes()).containsKey("connectivity");
    }

    private Requirements requirements() {
        return new Requirements(ProductCategory.LAPTOP, new BigDecimal("1500.00"), List.of("16 GB memory"),
                List.of("lightweight"), List.of(), null);
    }
}
