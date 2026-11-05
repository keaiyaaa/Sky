package com.tianshu.backend.device;

import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.common.FieldRules;
import com.tianshu.backend.common.PageResponse;
import com.tianshu.backend.security.CurrentUserProvider;
import com.tianshu.backend.sync.SyncJournal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static com.tianshu.backend.device.DeviceModels.*;

@Service
public class DeviceService {
    private final DeviceRepository repository;
    private final CurrentUserProvider currentUser;
    private final SyncJournal journal;
    public DeviceService(DeviceRepository repository, CurrentUserProvider currentUser, SyncJournal journal) {
        this.repository = repository; this.currentUser = currentUser; this.journal = journal;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceResponse> list(int page, int size) {
        String userId = currentUser.requireUserId();
        if (page < 0) throw ApiFailure.invalid("page", "页码不能为负数");
        if (size < 1 || size > 100) throw ApiFailure.invalid("size", "每页数量须为1至100");
        return repository.list(userId, page, size);
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(String id) {
        String userId = currentUser.requireUserId();
        return repository.find(userId, validId(id), false);
    }

    @Transactional
    public DeviceResponse create(CreateDeviceRequest request) {
        String userId = currentUser.requireUserId();
        FieldRules.version(request.expectedVersion(), true);
        String source = FieldRules.text(request.source(), "source", 32, true);
        if (!source.equals("DJI_SDK")) throw ApiFailure.invalid("source", "首期仅接受用户明确保存的DJI设备记录，演示不上传");
        var normalized = new CreateDeviceRequest(FieldRules.text(request.clientId(), "clientId", 128, true),
                FieldRules.text(request.droneName(), "droneName", 64, true),
                FieldRules.text(request.model(), "model", 64, false),
                FieldRules.text(request.serialNo(), "serialNo", 128, true),
                FieldRules.text(request.firmwareVersion(), "firmwareVersion", 64, false), source, 0L, null);
        return journal.execute(userId, request.idempotencyKey(), List.of("DEVICE_CREATE", normalized),
                DeviceResponse.class, () -> {
            String id;
            try { id = repository.insert(userId, normalized, Instant.now()); }
            catch (DuplicateKeyException exception) {
                throw new ApiFailure(HttpStatus.CONFLICT, "DEVICE_ALREADY_SAVED", "该账号已保存此设备或客户端标识");
            }
            return changed(userId, repository.find(userId, id, false));
        });
    }

    @Transactional
    public DeviceResponse update(String id, UpdateDeviceRequest request) {
        String userId = currentUser.requireUserId();
        String resourceId = validId(id);
        FieldRules.version(request.expectedVersion(), false);
        var normalized = new UpdateDeviceRequest(FieldRules.text(request.droneName(), "droneName", 64, true),
                FieldRules.text(request.model(), "model", 64, false),
                FieldRules.text(request.firmwareVersion(), "firmwareVersion", 64, false),
                request.expectedVersion(), null);
        return journal.execute(userId, request.idempotencyKey(), List.of("DEVICE_UPDATE", resourceId, normalized),
                DeviceResponse.class, () -> {
            repository.find(userId, resourceId, false);
            if (!repository.update(userId, resourceId, normalized, Instant.now())) throw ApiFailure.conflict();
            return changed(userId, repository.find(userId, resourceId, false));
        });
    }

    @Transactional
    public DeviceResponse delete(String id, DeleteDeviceRequest request) {
        String userId = currentUser.requireUserId();
        String resourceId = validId(id);
        FieldRules.version(request.expectedVersion(), false);
        return journal.execute(userId, request.idempotencyKey(),
                List.of("DEVICE_DELETE", resourceId, request.expectedVersion()), DeviceResponse.class, () -> {
            repository.find(userId, resourceId, false);
            if (!repository.delete(userId, resourceId, request.expectedVersion(), Instant.now())) throw ApiFailure.conflict();
            return changed(userId, repository.find(userId, resourceId, true));
        });
    }

    private DeviceResponse changed(String userId, DeviceResponse response) {
        journal.appendDevice(userId, response);
        return response;
    }

    private String validId(String id) {
        try {
            if (!id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) { throw ApiFailure.invalid("id", "设备标识无效"); }
    }
}
