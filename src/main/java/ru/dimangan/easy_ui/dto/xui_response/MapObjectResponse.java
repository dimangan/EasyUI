package ru.dimangan.easy_ui.dto.xui_response;

import lombok.Data;

import java.util.Map;

@Data
public class MapObjectResponse {
    private boolean success;
    private Map<String, Object> obj;
    private String msg;
}
