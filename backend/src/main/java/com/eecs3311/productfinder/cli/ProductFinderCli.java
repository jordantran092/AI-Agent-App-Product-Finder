package com.eecs3311.productfinder.cli;

import com.eecs3311.productfinder.application.AgentService;
import com.eecs3311.productfinder.application.BuyWaitRecommendation;
import com.eecs3311.productfinder.application.ChatFlowResponse;
import com.eecs3311.productfinder.application.NoteSaveResult;
import com.eecs3311.productfinder.application.ProductAddResult;
import com.eecs3311.productfinder.application.ProductService;
import com.eecs3311.productfinder.application.ProfileService;
import com.eecs3311.productfinder.application.ProfileSettings;
import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.application.SavedProductWorkflowService;
import com.eecs3311.productfinder.application.auth.AuthService;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.savedlist.SavedItem;
import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true")
public class ProductFinderCli implements CommandLineRunner {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AgentService agentService;
    private final SavedProductWorkflowService savedProductWorkflowService;
    private final ProductService productService;
    private final ProfileService profileService;
    private final SavedListService savedListService;
    private final String frontendUrl;

    private User currentUser;

    public ProductFinderCli(AuthService authService,
                            UserRepository userRepository,
                            AgentService agentService,
                            SavedProductWorkflowService savedProductWorkflowService,
                            ProductService productService,
                            ProfileService profileService,
                            SavedListService savedListService,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.agentService = agentService;
        this.savedProductWorkflowService = savedProductWorkflowService;
        this.productService = productService;
        this.profileService = profileService;
        this.savedListService = savedListService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void run(String... args) {
        ConsoleInput input = new ConsoleInput();
        System.out.println("Product Finder CLI. Type 'help' to see commands.");
        while (true) {
            String line = input.readLine("price-agent> ");
            if (line == null || line.trim().equalsIgnoreCase("exit")) {
                return;
            }
            if (line.isBlank()) {
                continue;
            }
            try {
                execute(line.trim(), input);
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                System.out.println("Error: " + (message == null ? exception.getClass().getSimpleName() : message));
            }
        }
    }

    private void execute(String line, ConsoleInput input) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length == 2 ? parts[1].trim() : "";
        switch (command) {
            case "help" -> printHelp();
            case "login" -> login(input);
            case "register" -> register(input);
            case "logout" -> logout();
            case "chat" -> requireUser(() -> chat(input));
            case "list" -> requireUser(this::listProducts);
            case "add" -> requireUser(() -> addProduct(argument));
            case "confirm-add" -> requireUser(() -> confirmAdd(argument));
            case "remove" -> requireUser(() -> removeProduct(argument, input));
            case "price" -> requireUser(() -> showBestPrice(argument));
            case "product" -> requireUser(() -> showProduct(argument));
            case "summary" -> requireUser(() -> showSummary(argument));
            case "note" -> requireUser(() -> saveNote(argument));
            case "profile" -> requireUser(this::showProfile);
            case "settings" -> requireUser(() -> updateSettings(input));
            case "share" -> requireUser(this::shareList);
            default -> System.out.println("Unknown command. Type 'help' for available commands.");
        }
    }

    private void login(ConsoleInput input) {
        String username = input.readLine("Username: ");
        String password = input.readPassword("Password: ");
        if (username == null || password == null) {
            System.out.println("Sign-in cancelled.");
            return;
        }
        authService.authenticate(username, password);
        currentUser = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
        System.out.println("Signed in as " + currentUser.getUsername() + ".");
    }

    private void register(ConsoleInput input) {
        String username = input.readLine("Username: ");
        String password = input.readPassword("Password (at least 12 characters): ");
        if (username == null || password == null) {
            System.out.println("Registration cancelled.");
            return;
        }
        User user = authService.register(username, password);
        authService.authenticate(username, password);
        currentUser = user;
        System.out.println("Account created. Signed in as " + currentUser.getUsername() + ".");
    }

    private void logout() {
        if (currentUser == null) {
            System.out.println("You are not signed in.");
            return;
        }
        currentUser = null;
        System.out.println("Signed out.");
    }

    private void chat(ConsoleInput input) {
        System.out.println("Chat started. Send a message, or use /confirm, /cancel, /retry, /buywait, or /back.");
        printChat(agentService.current(currentUser.getId()));
        while (true) {
            String message = input.readLine("chat> ");
            if (message == null || message.equalsIgnoreCase("/back") || message.equalsIgnoreCase("exit")) {
                return;
            }
            if (message.isBlank()) {
                continue;
            }
            ChatFlowResponse response;
            if (message.equalsIgnoreCase("/confirm")) {
                response = agentService.confirmRequirements(currentUser.getId());
            } else if (message.equalsIgnoreCase("/cancel")) {
                response = agentService.cancelConfirmation(currentUser.getId());
            } else if (message.equalsIgnoreCase("/retry")) {
                response = agentService.retryMatch(currentUser.getId());
            } else if (message.equalsIgnoreCase("/buywait")) {
                String urgency = input.readLine("How urgently do you need it? ");
                BuyWaitRecommendation recommendation = agentService.recommend(currentUser.getId(), urgency);
                System.out.println(recommendation.decision() + ": " + recommendation.reason());
                if (recommendation.priceHistory() == null) {
                    System.out.println("No price history is available.");
                }
                continue;
            } else {
                response = agentService.handleMessage(currentUser.getId(), message);
            }
            printChat(response);
        }
    }

    private void printChat(ChatFlowResponse response) {
        if (!response.history().isEmpty()) {
            var lastMessage = response.history().get(response.history().size() - 1);
            System.out.println(lastMessage.role() + ": " + lastMessage.content());
        }
        System.out.println("State: " + response.state());
        if (response.requirements() != null) {
            System.out.println("Requirements: " + response.requirements());
        }
        if (response.match() != null) {
            var match = response.match();
            System.out.printf("%s (%s) — %s at %s%n",
                    match.product().getName(), match.product().getModel(),
                    match.bestListing().getPrice(), match.bestListing().getRetailer().getName());
            System.out.println("Trade-offs: " + match.explanation());
        }
        if (response.noMatchMessage() != null) {
            System.out.println(response.noMatchMessage());
            if (!response.constraintsToRelax().isEmpty()) {
                System.out.println("Consider relaxing: " + String.join(", ", response.constraintsToRelax()));
            }
        }
    }

    private void listProducts() {
        var items = savedProductWorkflowService.getSavedItems(currentUser.getId());
        if (items.isEmpty()) {
            System.out.println("Your saved list is empty.");
            return;
        }
        for (SavedItem item : items) {
            Product product = item.getProduct();
            System.out.printf("%d  %s (%s) — %s%n", product.getId(), product.getName(),
                    product.getModel(), item.getTrustLabel());
        }
    }

    private void addProduct(String model) {
        if (model.isBlank()) {
            System.out.println("Usage: add <product-model>");
            return;
        }
        ProductAddResult result = savedProductWorkflowService.addOrSuggest(currentUser.getId(), model);
        System.out.println(result.status() + ": " + result.status().name().replace('_', ' '));
        if (result.product() != null) {
            System.out.printf("%s (%s), id %d%n", result.product().getName(),
                    result.product().getModel(), result.product().getId());
            if (result.status().name().equals("SUGGESTION")) {
                System.out.println("Run 'confirm-add " + result.product().getId() + "' to accept.");
            }
        }
        if (result.trustLabel() != null) {
            System.out.println("Review trust: " + result.trustLabel());
        }
    }

    private void confirmAdd(String productId) {
        long id = parseId(productId);
        ProductAddResult result = savedProductWorkflowService.confirmAdd(currentUser.getId(), id);
        System.out.println(switch (result.status()) {
            case ADDED -> "Product added to your saved list";
            case ALREADY_SAVED -> "Product is already in your saved list";
            case SUGGESTION -> "Did you mean this product? Confirm to add it";
            case NOT_FOUND -> "Product not found";
        });
        if (result.trustLabel() != null) {
            System.out.println("Review trust: " + result.trustLabel());
        }
    }

    private void removeProduct(String productId, ConsoleInput input) {
        long id = parseId(productId);
        String answer = input.readLine("Remove product " + id + " from your saved list? (y/N) ");
        if (!"y".equalsIgnoreCase(answer)) {
            System.out.println("No changes made.");
            return;
        }
        savedProductWorkflowService.remove(currentUser.getId(), id);
        System.out.println("Product removed.");
    }

    private void showBestPrice(String productId) {
        long id = parseId(productId);
        var listing = productService.bestPrice(currentUser.getId(), id);
        if (listing.isPresent()) {
            var offer = listing.get();
            System.out.printf("Best price: %s at %s (%s)%n", offer.getPrice(),
                    offer.getRetailer().getName(), offer.getCondition());
        } else {
            System.out.println("No retailer currently meets your settings.");
        }
    }

    private void showProduct(String productId) {
        long id = parseId(productId);
        var page = savedProductWorkflowService.productPage(currentUser.getId(), id);
        if (page.discontinued()) {
            System.out.println("This product has been discontinued and removed from your saved list.");
            return;
        }
        Product product = page.product();
        System.out.printf("%s — %s %s%n%s%n", product.getName(), product.getBrand(),
                product.getModel(), product.getDescription());
        System.out.println("Specifications: " + product.getAttributes());
        System.out.println("Review trust: " + page.savedItem().getTrustLabel());
        if (page.bestListing() == null) {
            System.out.println("No retailer currently meets your settings.");
        } else {
            System.out.printf("Best price: %s at %s%n", page.bestListing().getPrice(),
                    page.bestListing().getRetailer().getName());
        }
        System.out.println("Note: " + (page.savedItem().getNote() == null ? "(none)" : page.savedItem().getNote()));
    }

    private void showSummary(String productId) {
        var result = savedProductWorkflowService.summarize(currentUser.getId(), parseId(productId));
        System.out.println(result.summary());
    }

    private void saveNote(String argument) {
        String[] parts = argument.split("\\s+", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            System.out.println("Usage: note <product-id> <note text>");
            return;
        }
        NoteSaveResult result = savedProductWorkflowService.saveNote(
                currentUser.getId(), parseId(parts[0]), parts[1]);
        System.out.println(result.sanitized()
                ? "Unsupported control characters were removed; the note was saved."
                : "Note saved.");
    }

    private void showProfile() {
        ProfileSettings settings = profileService.get(currentUser.getId());
        System.out.println("Budget: " + (settings.budget() == null ? "no limit" : settings.budget()));
        System.out.println("Excluded brands: " + String.join(", ", settings.excludedBrands()));
        System.out.println("Allowed conditions: " + settings.conditions());
    }

    private void updateSettings(ConsoleInput input) {
        String budgetText = input.readLine("Maximum budget (blank for no limit): ").trim();
        BigDecimal budget = budgetText.isEmpty() ? null : new BigDecimal(budgetText);
        Set<String> brands = Arrays.stream(input.readLine("Excluded brands (comma-separated): ").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        String conditionText = input.readLine("Allowed conditions (NEW, USED, REFURBISHED; blank for all): ");
        Set<ProductCondition> conditions = conditionText.isBlank()
                ? Set.of()
                : Arrays.stream(conditionText.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> ProductCondition.valueOf(value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());
        profileService.update(currentUser.getId(), budget, brands, conditions);
        System.out.println("Profile settings saved.");
    }

    private void shareList() {
        String token = savedListService.createShareLink(currentUser.getId());
        System.out.println("Share link: " + frontendUrl + "/shared/" + token);
    }

    private long parseId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a numeric product ID");
        }
    }

    private void requireUser(Runnable action) {
        if (currentUser == null) {
            System.out.println("Sign in first with 'login' or create an account with 'register'.");
            return;
        }
        action.run();
    }

    private void printHelp() {
        System.out.println("""
                Commands:
                  register, login, logout
                  chat                              Start product search chat
                  list                              Show saved products
                  add <model>                       Add a product or get a suggestion
                  confirm-add <product-id>           Accept a suggested product
                  remove <product-id>                Remove a saved product
                  product <product-id>               Show saved product details
                  price <product-id>                 Show its best eligible price
                  summary <product-id>               Summarize product and reviews
                  note <product-id> <text>            Save a personal note
                  profile                            Show preferences
                  settings                           Update preferences
                  share                              Create a public list link
                  help, exit
                """);
    }

    private static final class ConsoleInput {
        private final java.util.Scanner scanner = new java.util.Scanner(System.in);
        private final java.io.Console console = System.console();

        private String readLine(String prompt) {
            if (console != null) {
                return console.readLine("%s", prompt);
            }
            System.out.print(prompt);
            System.out.flush();
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }

        private String readPassword(String prompt) {
            if (console != null) {
                char[] password = console.readPassword("%s", prompt);
                return password == null ? null : new String(password);
            }
            return readLine(prompt);
        }
    }
}
