package com.tianshu.backend.audit;

import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.common.FieldRules;
import com.tianshu.backend.security.CurrentUserProvider;
import com.tianshu.backend.sync.SyncJournal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static com.tianshu.backend.audit.FlightLogModels.*;

@Service
public class FlightLogService {
    private static final Set<String> SOURCES = Set.of("DEVICE","DJI_SIMULATOR","LOCAL_SIMULATION","MANUAL","UNKNOWN");
    private static final Set<String> CATEGORIES = Set.of("CONNECTION","SETTING","COMMAND","MISSION");
    private final CurrentUserProvider currentUser;
    private final FlightLogRepository repository;
    private final SyncJournal journal;
    public FlightLogService(CurrentUserProvider currentUser, FlightLogRepository repository, SyncJournal journal) {
        this.currentUser=currentUser; this.repository=repository; this.journal=journal;
    }
    @Transactional(readOnly=true)
    public LogPage list(int page, int size, String keyword, String source, String category, String from, String to) {
        String userId=currentUser.requireUserId();
        if (page<0) throw ApiFailure.invalid("page","页码不能为负数");
        if (size<1 || size>100) throw ApiFailure.invalid("size","每页数量为1至100");
        String query=FieldRules.text(keyword,"keyword",128,false);
        if (source!=null && !SOURCES.contains(source)) throw ApiFailure.invalid("source","未知来源");
        if (category!=null && !CATEGORIES.contains(category)) throw ApiFailure.invalid("category","未知分类");
        Instant start=from==null?null:instant(from,"from"), end=to==null?null:instant(to,"to");
        if (start!=null && end!=null && !start.isBefore(end)) throw ApiFailure.invalid("to","结束时间必须晚于开始时间");
        return repository.list(userId,new Query(page,size,query,source,category,start,end));
    }
    @Transactional
    public LogEntry create(CreateLogRequest request) {
        String userId=currentUser.requireUserId();
        FieldRules.version(request.expectedVersion(),true);
        String category=FieldRules.text(request.category(),"category",32,true);
        if (!CATEGORIES.contains(category) || category.equals("MISSION"))
            throw ApiFailure.invalid("category","运行事件须由运行模块提供只读视图，不重复上传");
        String source=FieldRules.text(request.source(),"source",32,true);
        if (!SOURCES.contains(source)) throw ApiFailure.invalid("source","未知来源");
        var normalized=new CreateLogRequest(FieldRules.text(request.clientId(),"clientId",128,true),category,
                safe(request.action(),"action",128,true),safe(request.result(),"result",64,true),source,
                safe(request.detail(),"detail",2000,false),safe(request.taskId(),"taskId",128,false),
                instant(request.occurredAt(),"occurredAt").toString(),0L,null);
        String hash=journal.fingerprint(normalized);
        return journal.execute(userId,request.idempotencyKey(),List.of("FLIGHT_LOG_CREATE",normalized),LogEntry.class,()->{
            var prior=repository.find(userId,normalized.clientId());
            if (prior.isPresent()) {
                if (!prior.get().requestHash().equals(hash)) throw new ApiFailure(HttpStatus.CONFLICT,"EVENT_ID_REUSED","事件编号已用于不同内容");
                return prior.get().entry();
            }
            LogEntry result=repository.insert(userId,normalized,hash);
            journal.appendChange(userId,"FLIGHT_LOG",result.id(),result.clientId(),"UPSERT",1,Instant.now(),result);
            return result;
        });
    }
    private static String safe(String value,String field,int max,boolean required) {
        // Allow newlines in explanatory details, normalize them before the common control-character check.
        String text=value==null?null:value.replace('\n',' ').replace('\r',' ').replace('\t',' ');
        return FieldRules.text(EventTextSanitizer.clean(FieldRules.text(text,field,max,required)),field,max,required);
    }
    private static Instant instant(String value,String field) {
        try {
            if (value==null || !value.endsWith("Z")) throw new IllegalArgumentException();
            Instant parsed=Instant.parse(value);
            if (parsed.isBefore(Instant.parse("1970-01-01T00:00:00Z")) || parsed.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException invalid) { throw ApiFailure.invalid(field,"使用RFC3339 UTC时间并以Z结尾"); }
    }
}
