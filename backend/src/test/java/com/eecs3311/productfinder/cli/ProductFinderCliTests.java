package com.eecs3311.productfinder.cli;

import com.eecs3311.productfinder.application.AgentService;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.application.SavedProductWorkflowService;
import com.eecs3311.productfinder.application.auth.AuthService;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductFinderCliTests {

    @Test
    void clearAndClsCommandsClearTheTerminalAndAppearInHelp() {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream("help\nclear\ncls\nexit\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(output);

            cli().run();

            String result = capturedOutput.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("clear, cls");
            assertThat(result).doesNotContain("Unknown command");
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }

    private ProductFinderCli cli() {
        return new ProductFinderCli(
                mock(AuthService.class),
                mock(UserRepository.class),
                mock(AgentService.class),
                mock(SavedProductWorkflowService.class),
                mock(ProductService.class),
                mock(ProfileService.class),
                mock(SavedListService.class),
                "http://localhost:5173"
        );
    }
}
