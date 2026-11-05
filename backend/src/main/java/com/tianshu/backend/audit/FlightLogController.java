package com.tianshu.backend.audit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.tianshu.backend.audit.FlightLogModels.*;

@RestController
@RequestMapping("/api/v1/flight-logs")
public class FlightLogController {
    private final FlightLogService service;
    public FlightLogController(FlightLogService service) { this.service=service; }
    @GetMapping public LogPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String keyword,@RequestParam(required=false) String source,
            @RequestParam(required=false) String category,@RequestParam(required=false) String from,@RequestParam(required=false) String to) {
        return service.list(page,size,keyword,source,category,from,to);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public LogEntry create(@RequestBody CreateLogRequest request) { return service.create(request); }
}
