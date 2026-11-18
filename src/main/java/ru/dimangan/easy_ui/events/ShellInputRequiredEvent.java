package ru.dimangan.easy_ui.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.dimangan.easy_ui.dto.internal.Shell;

@Data
@AllArgsConstructor
public class ShellInputRequiredEvent  implements ApplicationEvent{
    private Shell shell;
    private String input;
}
