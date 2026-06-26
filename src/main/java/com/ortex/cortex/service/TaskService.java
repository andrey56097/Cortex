package com.ortex.cortex.service;

import com.ortex.cortex.dto.TaskRequest;
import com.ortex.cortex.dto.TaskResponse;
import com.ortex.cortex.exception.TaskNotFoundException;
import com.ortex.cortex.model.Task;
import com.ortex.cortex.model.TaskStatus;
import com.ortex.cortex.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskResponse createTask(TaskRequest request) {
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(TaskStatus.valueOf(request.getStatus()))
                .build();

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    public List<TaskResponse> getAllTasks(String status) {
        List<Task> tasks;

        if (status != null && !status.isBlank()) {
            TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
            tasks = taskRepository.findByStatus(taskStatus);
        } else {
            tasks = taskRepository.findAll();
        }

        return tasks.stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return TaskResponse.from(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(TaskStatus.valueOf(request.getStatus()));

        Task updated = taskRepository.save(task);
        return TaskResponse.from(updated);
    }

    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }
}
