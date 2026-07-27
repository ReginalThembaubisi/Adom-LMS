package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerProfileResponseDto {
    private Long id;
    private String fullName;
    private String email;
    private String username;
    private List<String> assignedCategories;
}
