package com.ortex.cortex.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ortex.cortex.dto.TaskRequest;
import com.ortex.cortex.dto.TaskResponse;
import com.ortex.cortex.exception.TaskNotFoundException;
import com.ortex.cortex.exception.GlobalExceptionHandler;
import com.ortex.cortex.model.TaskStatus;
import com.ortex.cortex.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TaskResponse sampleResponse(Long id, String title, TaskStatus status) {
        return TaskResponse.builder()
                .id(id)
                .title(title)
                .description("Desc")
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void createTask_shouldReturn201() throws Exception {
        // Given
        TaskRequest request = new TaskRequest("New Task", "Desc", "TODO");
        TaskResponse response = sampleResponse(1L, "New Task", TaskStatus.TODO);
        given(taskService.createTask(any(TaskRequest.class))).willReturn(response);

        // When / Then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("New Task"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createTask_withEmptyTitle_shouldReturn400() throws Exception {
        // Given
        TaskRequest request = new TaskRequest("", "Desc", "TODO");

        // When / Then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
    }

    @Test
    void getAllTasks_shouldReturn200() throws Exception {
        // Given
        List<TaskResponse> tasks = List.of(
                sampleResponse(1L, "Task 1", TaskStatus.TODO),
                sampleResponse(2L, "Task 2", TaskStatus.DONE)
        );
        given(taskService.getAllTasks(null)).willReturn(tasks);

        // When / Then
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Task 1"));
    }

    @Test
    void getAllTasks_withStatusFilter_shouldCallServiceWithStatus() throws Exception {
        // Given
        given(taskService.getAllTasks("DONE")).willReturn(List.of());

        // When / Then
        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk());
    }

    @Test
    void getTaskById_shouldReturn200() throws Exception {
        // Given
        TaskResponse response = sampleResponse(1L, "My Task", TaskStatus.IN_PROGRESS);
        given(taskService.getTaskById(1L)).willReturn(response);

        // When / Then
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My Task"));
    }

    @Test
    void getTaskById_whenNotFound_shouldReturn404() throws Exception {
        // Given
        given(taskService.getTaskById(999L)).willThrow(new TaskNotFoundException(999L));

        // When / Then
        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found with id: 999"));
    }

    @Test
    void updateTask_shouldReturn200() throws Exception {
        // Given
        TaskRequest request = new TaskRequest("Updated", "New desc", "DONE");
        TaskResponse response = sampleResponse(1L, "Updated", TaskStatus.DONE);
        given(taskService.updateTask(eq(1L), any(TaskRequest.class))).willReturn(response);

        // When / Then
        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void updateTask_whenNotFound_shouldReturn404() throws Exception {
        // Given
        TaskRequest request = new TaskRequest("Updated", "Desc", "TODO");
        given(taskService.updateTask(eq(999L), any(TaskRequest.class)))
                .willThrow(new TaskNotFoundException(999L));

        // When / Then
        mockMvc.perform(put("/api/tasks/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_shouldReturn204() throws Exception {
        // When / Then
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTask_whenNotFound_shouldReturn404() throws Exception {
        // Given
        willThrow(new TaskNotFoundException(999L)).given(taskService).deleteTask(999L);

        // When / Then
        mockMvc.perform(delete("/api/tasks/999"))
                .andExpect(status().isNotFound());
    }
}
