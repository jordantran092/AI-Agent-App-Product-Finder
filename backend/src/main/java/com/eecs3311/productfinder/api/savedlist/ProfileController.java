package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.api.CurrentUserId;
import com.eecs3311.productfinder.application.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserId currentUserId;

    public ProfileController(ProfileService profileService, CurrentUserId currentUserId) {
        this.profileService = profileService;
        this.currentUserId = currentUserId;
    }

    @GetMapping
    public ProfileSettingsResponse get(@AuthenticationPrincipal UserDetails principal) {
        return ProfileSettingsResponse.from(profileService.get(currentUserId.resolve(principal)));
    }

    @PutMapping
    public ProfileSettingsResponse update(@AuthenticationPrincipal UserDetails principal,
                                          @Valid @RequestBody ProfileUpdateRequest request) {
        return ProfileSettingsResponse.from(profileService.update(
                currentUserId.resolve(principal),
                request.budget(),
                request.excludedBrands(),
                request.conditions()
        ));
    }
}
