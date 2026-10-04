package com.nexa.user;

import com.nexa.common.security.AuthenticatedUser;
import com.nexa.user.dto.MeResponse;
import com.nexa.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser current) {
        return userService.getMe(current);
    }

    @PatchMapping("/me")
    public MeResponse updateMe(@AuthenticationPrincipal AuthenticatedUser current,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(current, request);
    }
}
