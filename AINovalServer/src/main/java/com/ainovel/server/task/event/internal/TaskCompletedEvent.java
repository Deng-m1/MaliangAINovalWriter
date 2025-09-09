package com.ainovel.server.task.event.internal;

/**
 * 任务完成事件
 */
public class TaskCompletedEvent extends TaskApplicationEvent {
    
    private final Object result;
    
    /**
     * 创建任务完成事件
     * 
     * @param source 事件源
     * @param taskId 任务ID
     * @param taskType 任务类型
     * @param userId 用户ID
     * @param result 任务结果
     */
    public TaskCompletedEvent(Object source, String taskId, String taskType, String userId, Object result) {
        super(source, taskId, taskType, userId);
        this.result = result;
    }
    
    /**
     * 获取任务结果
     * 
     * @return 任务结果
     */
    public Object getResult() {
        return result;
    }
} 