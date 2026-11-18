package ru.dimangan.easy_ui.dto.xui_response;

import lombok.Data;

import java.util.List;

@Data
public class ListResponse <T> {
    private boolean success;
    private List<T> obj;
    private String msg;
}
