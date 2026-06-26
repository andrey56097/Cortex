package com.ortex.cortex.service;

import com.ortex.cortex.dto.TaskRequest;
import com.ortex.cortex.dto.TaskResponse;
import com.ortex.cortex.exception.TaskNotFoundException;
import com.ortex.cortex.model.Task;
import com.ortex.cortex.model.TaskStatus;
import com.ortex.cortex.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private Task createSampleTask(Long id, String title, TaskStatus status) {
        return Task.builder()
                .id(id)
                .title(title)
                .description("Description of " + title)
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void createTask_shouldReturnCreatedTask() {
        // Given
        TaskRequest request = TaskRequest.builder()
                .title("New Task")
                .description("Task desc")
                .status("TODO")
                .build();

        Task savedTask = createSampleTask(1L, "New Task", TaskStatus.TODO);
        given(taskRepository.save(any(Task.class))).willReturn(savedTask);

        // When
        TaskResponse response = taskService.createTask(request);

        // Then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("New Task");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void getAllTasks_withoutStatus_shouldReturnAll() {
        // Given
        List<Task> tasks = List.of(
                createSampleTask(1L, "Task 1", TaskStatus.TODO),
                createSampleTask(2L, "Task 2", TaskStatus.DONE)
        );
        given(taskRepository.findAll()).willReturn(tasks);

        // When
        List<TaskResponse> result = taskService.getAllTasks(null);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void getAllTasks_withStatusFilter_shouldReturnFiltered() {
        // Given
        List<Task> doneTasks = List.of(
                createSampleTask(2L, "Done Task", TaskStatus.DONE)
        );
        given(taskRepository.findByStatus(TaskStatus.DONE)).willReturn(doneTasks);

        // When
        List<TaskResponse> result = taskService.getAllTasks("DONE");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void getTaskById_whenExists_shouldReturnTask() {
        // Given
        Task task = createSampleTask(1L, "Find me", TaskStatus.TODO);
        given(taskRepository.findById(1L)).willReturn(Optional.of(task));

        // When
        TaskResponse response = taskService.getTaskById(1L);

        // Then
        assertThat(response.getTitle()).isEqualTo("Find me");
    }

    @Test
    void getTaskById_whenNotExists_shouldThrow() {
        // Given
        given(taskRepository.findById(999L)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.getTaskById(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void updateTask_shouldUpdateAndReturn() {
        // Given
        Task existingTask = createSampleTask(1L, "Old Title", TaskStatus.TODO);
        TaskRequest request = TaskRequest.builder()
                .title("Updated Title")
                .description("Updated desc")
                .status("DONE")
                .build();

        given(taskRepository.findById(1L)).willReturn(Optional.of(existingTask));
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        TaskResponse response = taskService.updateTask(1L, request);

        // Then
        assertThat(response.getTitle()).isEqualTo("Updated Title");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void updateTask_whenNotExists_shouldThrow() {
        // Given
        TaskRequest request = TaskRequest.builder().title("X").description("Y").status("TODO").build();
        given(taskRepository.findById(999L)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.updateTask(999L, request))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void deleteTask_whenExists_shouldDelete() {
        // Given
        given(taskRepository.existsById(1L)).willReturn(true);

        // When
        taskService.deleteTask(1L);

        // Then
        then(taskRepository).should().deleteById(1L);
    }

    @Test
    void deleteTask_whenNotExists_shouldThrow() {
        // Given
        given(taskRepository.existsById(999L)).willReturn(false);

        // When / Then
        assertThatThrownBy(() -> taskService.deleteTask(999L))
                .isInstanceOf(TaskNotFoundException.class);

        then(taskRepository).should(never()).deleteById(any());
    }
}
