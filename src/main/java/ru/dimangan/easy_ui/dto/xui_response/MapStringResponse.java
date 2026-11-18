package ru.dimangan.easy_ui.dto.xui_response;

import lombok.Data;

import java.util.Map;

@Data
public class MapStringResponse {
    boolean success;
    Map<String, String> obj;
}
