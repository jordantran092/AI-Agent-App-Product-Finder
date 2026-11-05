package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.api.CurrentUserId;
import com.eecs3311.productfinder.application.ProductAddResult;
import com.eecs3311.productfinder.application.ProductAddStatus;
import com.eecs3311.productfinder.application.SavedProductWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved-list")
public class SavedListController {

    private final SavedProductWorkflowService workflowService;
    private final CurrentUserId currentUserId;

    public SavedListController(SavedProductWorkflowService workflowService, CurrentUserId currentUserId) {
        this.workflowService = workflowService;
        this.currentUserId = currentUserId;
    }

    @GetMapping
    public SavedListResponse get(@AuthenticationPrincipal UserDetails principal) {
        return SavedListResponse.from(workflowService.getSavedItems(currentUserId.resolve(principal)));
    }

    @PostMapping("/items")
    public ResponseEntity<AddProductResponse> add(@AuthenticationPrincipal UserDetails principal,
                                                  @Valid @RequestBody AddProductRequest request) {
        return addResponse(workflowService.addOrSuggest(
                currentUserId.resolve(principal), request.modelName()));
    }

    @PostMapping("/items/{productId}")
    public ResponseEntity<AddProductResponse> confirm(@AuthenticationPrincipal UserDetails principal,
                                                      @PathVariable long productId) {
        return addResponse(workflowService.confirmAdd(currentUserId.resolve(principal), productId));
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal UserDetails principal, @PathVariable long productId) {
        workflowService.remove(currentUserId.resolve(principal), productId);
    }

    @PutMapping("/items/{productId}/note")
    public NoteUpdateResponse saveNote(@AuthenticationPrincipal UserDetails principal,
                                       @PathVariable long productId,
                                       @Valid @RequestBody NoteUpdateRequest request) {
        return NoteUpdateResponse.from(workflowService.saveNote(
                currentUserId.resolve(principal), productId, request.note()));
    }

    private ResponseEntity<AddProductResponse> addResponse(ProductAddResult result) {
        HttpStatus status = switch (result.status()) {
            case ADDED -> HttpStatus.CREATED;
            case ALREADY_SAVED, SUGGESTION -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(AddProductResponse.from(result));
    }
}
