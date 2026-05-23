package com.socialfeed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {
    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must be less than 1000 characters")
    private String content;

    @NotNull(message = "At least one source is required")
    @Size(min = 1, max = 3, message = "A post must have between 1 and 3 sources")
    private List<String> sources;

    @Size(max = 3, message = "A post can have at most 3 topic tags")
    private List<String> topicTags;
}
