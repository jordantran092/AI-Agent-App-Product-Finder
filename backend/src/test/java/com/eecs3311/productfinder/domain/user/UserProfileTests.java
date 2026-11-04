package com.eecs3311.productfinder.domain.user;

import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserProfileTests {

    @Test
    void profileIsAttachedToItsUserAndCopiesPreferences() {
        User user = new User("test-user", "hashed-password");
        Set<String> excludedBrands = new HashSet<>(Set.of("Test Brand"));
        UserProfile profile = new UserProfile(user, new BigDecimal("100.00"), excludedBrands,
                Set.of(ProductCondition.NEW));
        excludedBrands.add("Another Brand");

        assertThat(user.getProfile()).isSameAs(profile);
        assertThat(profile.getExcludedBrands()).containsExactly("Test Brand");
    }

    @Test
    void rejectsNegativeBudget() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserProfile(new User("test-user", "hashed-password"),
                        new BigDecimal("-0.01"), Set.of(), Set.of()));
    }
}
