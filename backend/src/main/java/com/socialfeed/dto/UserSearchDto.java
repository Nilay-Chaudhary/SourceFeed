package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSearchDto {
    private Long id;
    private String username;
    private String displayName;
    private Double userTrustScore;
    private boolean me;
    private boolean following;
}
