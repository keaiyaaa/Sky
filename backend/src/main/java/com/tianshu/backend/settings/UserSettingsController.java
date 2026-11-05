package com.tianshu.backend.settings;

import org.springframework.web.bind.annotation.*;
import static com.tianshu.backend.settings.SettingsModels.*;

@RestController @RequestMapping("/api/v1/settings")
public class UserSettingsController {
    private final UserSettingsService service;
    public UserSettingsController(UserSettingsService service) { this.service=service; }
    @GetMapping public SettingsResponse get() { return service.get(); }
    @PutMapping public SettingsResponse put(@RequestBody UpdateRequest request) { return service.update(request); }
}
