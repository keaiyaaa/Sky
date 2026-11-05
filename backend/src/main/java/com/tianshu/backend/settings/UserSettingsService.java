package com.tianshu.backend.settings;

import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.security.CurrentUserProvider;
import com.tianshu.backend.sync.SyncJournal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static com.tianshu.backend.settings.SettingsModels.*;

@Service
public class UserSettingsService {
    private final SettingsRepository repository;
    private final CurrentUserProvider currentUser;
    private final SyncJournal journal;
    public UserSettingsService(SettingsRepository repository,CurrentUserProvider currentUser,SyncJournal journal) {
        this.repository=repository; this.currentUser=currentUser; this.journal=journal;
    }
    @Transactional(readOnly=true) public SettingsResponse get() {
        String user=currentUser.requireUserId();
        if(!repository.userExists(user)) throw ApiFailure.notFound();
        return repository.find(user).orElseGet(()->new SettingsResponse(null,CLIENT_ID,user,Values.defaults(),0,null,false,null));
    }
    @Transactional public SettingsResponse update(UpdateRequest request) {
        String user=currentUser.requireUserId();
        if(request.expectedVersion()==null || request.expectedVersion()<0) throw ApiFailure.invalid("expectedVersion","提交当前版本，首次保存为0");
        Values settings=SettingsRules.normalize(request.settings());
        return journal.execute(user,request.idempotencyKey(),List.of("USER_SETTING_PUT",settings,request.expectedVersion()),SettingsResponse.class,()->{
            var current=repository.find(user);
            long actualVersion=current.map(SettingsResponse::version).orElse(0L);
            if(actualVersion!=request.expectedVersion()) throw ApiFailure.conflict();
            Instant at=Instant.now();
            if(current.isEmpty()) repository.insert(user,settings,at);
            else if(!repository.update(user,settings,request.expectedVersion(),at)) throw ApiFailure.conflict();
            SettingsResponse response=repository.find(user).orElseThrow();
            journal.appendChange(user,"USER_SETTING",response.id(),CLIENT_ID,"UPSERT",response.version(),response.updatedAt(),response);
            return response;
        });
    }
}
