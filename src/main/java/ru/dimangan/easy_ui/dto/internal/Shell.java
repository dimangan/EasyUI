package ru.dimangan.easy_ui.dto.internal;

import com.jcraft.jsch.ChannelShell;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import ru.dimangan.easy_ui.enums.ShellStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Data
@Slf4j
public class Shell implements AutoCloseable{
    private ChannelShell shell;
    private InputStream inputStream;
    private OutputStream outputStream;

    private final AtomicReference<ShellStatus> status = new AtomicReference<>(ShellStatus.IDLE);
    private int outputBufferSize = 256;
    private final AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder());
    private String currentCommandUuid = null;
    private Map<String, String> userInput = new HashMap<>();
    private String nextUserInput = null;

    public Shell(ChannelShell shell, InputStream inputStream, OutputStream outputStream) {
        this.shell = shell;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public synchronized void send(String command) throws IOException {
        if(command != null){
            outputStream.write(String.format("%s\n", command).getBytes());
            outputStream.flush();
        }
    }

    public void reset(){
        output.set(new StringBuilder());
        status.set(ShellStatus.IDLE);
        currentCommandUuid = null;
        userInput.clear();
    }


    public void setStatus(ShellStatus newStatus){
        synchronized (status){
            status.set(newStatus);
        }
    }

    public void appendOutput(String partialOutput){
        synchronized (output){
            output.get().append(partialOutput);
            int excess = output.get().length() - outputBufferSize;
            if(excess > 0){
                output.get().delete(0, excess);
            }
        }
    }

    public void clearOutput(){
        synchronized (output){
            output.set(new StringBuilder());
        }
    }

    public String getOutput(){
        synchronized (output){
            return output.get().toString();
        }
    }

    @Override
    public void close() {
        try {
            if(shell.isConnected()){
                shell.disconnect();
            }
            if(shell.getSession().isConnected()){
                shell.getSession().disconnect();
            }
        }
        catch (Exception e) {/**/}
    }
}
