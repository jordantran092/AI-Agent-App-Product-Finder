package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.application.SavedListService;
import com.eecs3311.productfinder.api.CurrentUserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class SharedListController {

    private final SavedListService savedListService;
    private final CurrentUserId currentUserId;
    private final String frontendUrl;

    public SharedListController(SavedListService savedListService, CurrentUserId currentUserId,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.savedListService = savedListService;
        this.currentUserId = currentUserId;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @PostMapping("/api/saved-list/share")
    public ShareLinkResponse share(@AuthenticationPrincipal UserDetails principal) {
        String token = savedListService.createShareLink(currentUserId.resolve(principal));
        String url = UriComponentsBuilder.fromUriString(frontendUrl)
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
