package com.ainovel.server.task.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.ainovel.server.repository.BackgroundTaskRepository;
import com.ainovel.server.task.model.BackgroundTask;
import com.ainovel.server.task.model.TaskStatus;
import com.ainovel.server.task.service.TaskStateService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TaskStateService接口的响应式实现
 */
@Slf4j
@Service
public class TaskStateServiceImpl implements TaskStateService {
    
    private final BackgroundTaskRepository taskRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public TaskStateServiceImpl(BackgroundTaskRepository taskRepository, 
                             ReactiveMongoTemplate mongoTemplate,
                             ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> createTask(String userId, String taskType, Object parameters, String parentTaskId) {
        return createSubTask(userId, taskType, parameters, parentTaskId)
                .map(BackgroundTask::getId);
    }
    
    /**
     * 创建无父任务的后台任务
     * 此方法是一个便捷方法，内部调用createSubTask，简单封装了一下
     * 
     * @param userId 用户ID
     * @param taskType 任务类型
     * @param parameters 任务参数
     * @return 创建的任务实体
     */
    public Mono<BackgroundTask> createTask(String userId, String taskType, Object parameters) {
        return createSubTask(userId, taskType, parameters, null);
    }
    
    @Override
    public Mono<BackgroundTask> createSubTask(String userId, String taskType, Object parameters, String parentTaskId) {
        BackgroundTask task = new BackgroundTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setStatus(TaskStatus.QUEUED);
        task.setParameters(parameters);
        task.setParentTaskId(parentTaskId);
        task.setRetryCount(0);
        
        // 设置时间戳
        BackgroundTask.TaskTimestamps timestamps = new BackgroundTask.TaskTimestamps();
        Instant now = Instant.now();
        timestamps.setCreatedAt(now);
        timestamps.setUpdatedAt(now);
        task.setTimestamps(timestamps);
        
        return taskRepository.save(task);
    }
    
    /**
     * 通过ID查找任务
     * 
     * @param taskId 任务ID
     * @return 包含任务的Mono，如果找不到则返回empty
     */
    public Mono<BackgroundTask> findById(String taskId) {
        return taskRepository.findById(taskId);
    }
    
    @Override
    public Mono<Boolean> trySetRunning(String taskId) {
        return trySetRunning(taskId, "default-node");
    }
    
    @Override
    public Mono<Boolean> trySetRunning(String taskId, String executionNodeId) {
        Instant now = Instant.now();
        
        // 使用原子性查询和更新操作
        Query query = new Query(Criteria.where("_id").is(taskId)
                                   .and("status").in(TaskStatus.QUEUED, TaskStatus.RETRYING));
        
        Update update = new Update()
                .set("status", TaskStatus.RUNNING)
                .set("executionNodeId", executionNodeId)
                .set("lastAttemptTimestamp", now)
                .set("timestamps.startedAt", now)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.findAndModify(query, update, BackgroundTask.class)
                .map(task -> true)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error when trying to set task {} to running state: {}", taskId, e.getMessage());
                    return Mono.just(false);
                });
    }
    
    @Override
    public Mono<Void> recordProgress(String taskId, Object progressData) {
        Query query = new Query(Criteria.where("_id").is(taskId)
                                   .and("status").is(TaskStatus.RUNNING));
        
        Update update = new Update()
                .set("progress", progressData)
                .set("timestamps.updatedAt", Instant.now());
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<Void> recordCompletion(String taskId, Object result) {
        Instant now = Instant.now();
        
        Query query = new Query(Criteria.where("_id").is(taskId)
                                   .and("status").is(TaskStatus.RUNNING));
        
        Update update = new Update()
                .set("status", TaskStatus.COMPLETED)
                .set("result", result)
                .set("timestamps.completedAt", now)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<Void> recordFailure(String taskId, Map<String, Object> errorInfo, boolean isDeadLetter) {
        Instant now = Instant.now();
        TaskStatus newStatus = isDeadLetter ? TaskStatus.DEAD_LETTER : TaskStatus.FAILED;
        
        Query query = new Query(Criteria.where("_id").is(taskId));
        
        Update update = new Update()
                .set("status", newStatus)
                .set("errorInfo", errorInfo)
                .set("timestamps.updatedAt", now);
        
        if (isDeadLetter) {
            update.set("timestamps.completedAt", now); // 死信也视为一种"完成"
        }
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<Void> recordFailure(String taskId, Throwable error, boolean isDeadLetter) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("message", error.getMessage());
        errorInfo.put("type", error.getClass().getName());
        
        // 如果有堆栈信息，最多收集10层
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            List<String> stackTraceList = Arrays.stream(stackTrace)
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList());
            errorInfo.put("stackTrace", stackTraceList);
        }
        
        return recordFailure(taskId, errorInfo, isDeadLetter);
    }
    
    @Override
    public Mono<Void> recordRetrying(String taskId, int retryCount, Throwable error, Instant nextAttemptTime) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("message", error.getMessage());
        errorInfo.put("type", error.getClass().getName());
        
        // 如果有堆栈信息，最多收集10层
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            List<String> stackTraceList = Arrays.stream(stackTrace)
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList());
            errorInfo.put("stackTrace", stackTraceList);
        }
        
        Instant now = Instant.now();
        
        Query query = new Query(Criteria.where("_id").is(taskId));
        
        Update update = new Update()
                .set("status", TaskStatus.RETRYING)
                .set("errorInfo", errorInfo)
                .set("retryCount", retryCount)
                .set("nextAttemptTimestamp", nextAttemptTime)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<Void> recordRetry(String taskId, Map<String, Object> errorInfo, Instant nextAttemptAt) {
        Instant now = Instant.now();
        
        Query query = new Query(Criteria.where("_id").is(taskId));
        
        Update update = new Update()
                .set("status", TaskStatus.RETRYING)
                .set("errorInfo", errorInfo)
                .set("nextAttemptTimestamp", nextAttemptAt)
                .inc("retryCount", 1)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<Void> recordCancellation(String taskId) {
        Instant now = Instant.now();
        
        Query query = new Query(Criteria.where("_id").is(taskId));
        
        Update update = new Update()
                .set("status", TaskStatus.CANCELLED)
                .set("timestamps.completedAt", now)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    @Override
    public Mono<BackgroundTask> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }
    
    @Override
    public Flux<BackgroundTask> getUserTasks(String userId, TaskStatus status, int page, int size) {
        if (status != null) {
            return taskRepository.findByUserIdAndStatus(userId, status, PageRequest.of(page, size));
        } else {
            return taskRepository.findByUserId(userId, PageRequest.of(page, size));
        }
    }
    
    @Override
    public Flux<BackgroundTask> getSubTasks(String parentTaskId) {
        return taskRepository.findByParentTaskId(parentTaskId);
    }

    @Override
    public Mono<Void> updateSubTaskStatusSummary(String parentTaskId, String childTaskId, 
                                               TaskStatus oldStatus, TaskStatus newStatus) {
        if (parentTaskId == null) {
            return Mono.empty();
        }
            
        return Mono.zip(
                // 减少旧状态计数
                decrementStatusCount(parentTaskId, oldStatus),
                // 增加新状态计数
                incrementStatusCount(parentTaskId, newStatus)
            ).then();
    }
    
    private Mono<Void> decrementStatusCount(String parentTaskId, TaskStatus status) {
        if (status == null) {
            return Mono.empty(); // 如果是初始状态变更（无旧状态），不需要减少
        }
        
        String statusKey = "subTaskStatusSummary." + status.name();
        Query query = new Query(Criteria.where("_id").is(parentTaskId));
        Update update = new Update().inc(statusKey, -1);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }
    
    private Mono<Void> incrementStatusCount(String parentTaskId, TaskStatus status) {
        String statusKey = "subTaskStatusSummary." + status.name();
        Query query = new Query(Criteria.where("_id").is(parentTaskId));
        Update update = new Update().inc(statusKey, 1);
        
        return mongoTemplate.updateFirst(query, update, BackgroundTask.class)
                .then();
    }

    @Override
    public Mono<Boolean> cancelTask(String taskId, String userId) {
        Instant now = Instant.now();
        
        Query query = new Query(Criteria.where("_id").is(taskId)
                                   .and("userId").is(userId)
                                   .and("status").in(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.RETRYING));
        
        Update update = new Update()
                .set("status", TaskStatus.CANCELLED)
                .set("timestamps.completedAt", now)
                .set("timestamps.updatedAt", now);
        
        return mongoTemplate.findAndModify(query, update, BackgroundTask.class)
                .map(task -> true)
                .defaultIfEmpty(false);
    }
} 