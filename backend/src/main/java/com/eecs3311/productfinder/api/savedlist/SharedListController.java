package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.api.CurrentUserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class SharedListController {

    private final SavedListService savedListService;
    private final CurrentUserId currentUserId;

    public SharedListController(SavedListService savedListService, CurrentUserId currentUserId) {
        this.savedListService = savedListService;
        this.currentUserId = currentUserId;
    }

    @PostMapping("/api/saved-list/share")
    public ShareLinkResponse share(@AuthenticationPrincipal UserDetails principal) {
        String token = savedListService.createShareLink(currentUserId.resolve(principal));
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/shared/{token}")
                .buildAndExpand(token)
                .toUriString();
        return new ShareLinkResponse(token, url);
    }

    @GetMapping("/api/shared-lists/{token}")
    public SharedListResponse view(@PathVariable String token) {
        return SharedListResponse.from(savedListService.getSharedList(token));
    }
}
