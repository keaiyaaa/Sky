package com.tianshu.backend.profile;

import org.springframework.web.bind.annotation.*;
import static com.tianshu.backend.profile.ProfileModels.*;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service = service; }
    @GetMapping public ProfileResponse get() { return service.get(); }
    @PatchMapping public ProfileResponse update(@RequestBody UpdateProfileRequest request) {
        return service.update(request);
    }
}
