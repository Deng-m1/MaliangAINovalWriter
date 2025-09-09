package com.ainovel.server.task.event.internal;

/**
 * 任务开始事件
 */
public class TaskStartedEvent extends TaskApplicationEvent {
    
    private final String executionNodeId;
    
    /**
     * 创建任务开始事件
     * 
     * @param source 事件源
     * @param taskId 任务ID
     * @param taskType 任务类型
     */
    public TaskStartedEvent(Object source, String taskId, String taskType) {
        this(source, taskId, taskType, null, null);
    }
    
    /**
     * 创建任务开始事件（完整信息）
     * 
     * @param source 事件源
     * @param taskId 任务ID
     * @param taskType 任务类型
     * @param userId 用户ID
     * @param executionNodeId 执行节点ID
     */
    public TaskStartedEvent(Object source, String taskId, String taskType, String userId, String executionNodeId) {
        super(source, taskId, taskType, userId);
        this.executionNodeId = executionNodeId;
    }
    
    /**
     * 获取执行节点ID
     * 
     * @return 执行节点ID
     */
    public String getExecutionNodeId() {
        return executionNodeId;
    }
} 