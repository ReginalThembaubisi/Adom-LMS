package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A short-lived ticket authorising one browser fetch of one submission file. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewTicketResponse {

    private String ticket;
    private long expiresAtEpochSecond;
}
