package ru.dimangan.easy_ui.dto.xui_response;

import lombok.Data;

@Data
public class StringResponse {
    private boolean success;
    private String obj;
    private String msg;
}
