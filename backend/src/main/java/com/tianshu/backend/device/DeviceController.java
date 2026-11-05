package com.tianshu.backend.device;

import com.tianshu.backend.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.tianshu.backend.device.DeviceModels.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService service;
    public DeviceController(DeviceService service) { this.service = service; }
    @GetMapping public PageResponse<DeviceResponse> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.list(page, size); }
    @GetMapping("/{id}") public DeviceResponse get(@PathVariable String id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse create(@RequestBody CreateDeviceRequest request) { return service.create(request); }
    @PutMapping("/{id}") public DeviceResponse update(@PathVariable String id, @RequestBody UpdateDeviceRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}") public DeviceResponse delete(@PathVariable String id, @RequestBody DeleteDeviceRequest request) {
        return service.delete(id, request);
    }
}
