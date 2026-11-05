package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.api.CurrentUserId;
import com.eecs3311.productfinder.application.ProductPageResult;
import com.eecs3311.productfinder.application.SavedProductWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductPageController {

    private final SavedProductWorkflowService workflowService;
    private final CurrentUserId currentUserId;

    public ProductPageController(SavedProductWorkflowService workflowService, CurrentUserId currentUserId) {
        this.workflowService = workflowService;
        this.currentUserId = currentUserId;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductPageResponse> get(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable long productId) {
        ProductPageResult result = workflowService.productPage(currentUserId.resolve(principal), productId);
        ProductPageResponse response = ProductPageResponse.from(result);
        return result.discontinued()
                ? ResponseEntity.status(HttpStatus.GONE).body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/summary")
    public ReviewSummaryResponse summarize(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable long productId) {
        return ReviewSummaryResponse.from(
                workflowService.summarize(currentUserId.resolve(principal), productId));
    }
}
