package com.tianshu.backend.profile;

import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.common.FieldRules;
import com.tianshu.backend.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static com.tianshu.backend.profile.ProfileModels.*;

@Service
public class ProfileService {
    private final ProfileRepository repository;
    private final CurrentUserProvider currentUser;
    public ProfileService(ProfileRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository; this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() { return repository.find(currentUser.requireUserId()); }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest request) {
        String userId = currentUser.requireUserId();
        FieldRules.version(request.expectedVersion(), false);
        String nickname = FieldRules.text(request.nickname(), "nickname", 20, true);
        String email = FieldRules.text(request.email(), "email", 254, false);
        String organization = FieldRules.text(request.organization(), "organization", 60, false);
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw ApiFailure.invalid("email", "邮箱格式不正确");
        }
        repository.find(userId);
        if (!repository.update(userId, new UpdateProfileRequest(nickname, email, organization,
                request.expectedVersion()), Instant.now())) throw ApiFailure.conflict();
        return repository.find(userId);
    }
}
