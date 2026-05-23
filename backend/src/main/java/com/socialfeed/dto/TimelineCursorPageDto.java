package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineCursorPageDto {
    private List<PostDto> items;
    private String nextCursor;
    private boolean hasMore;
}
