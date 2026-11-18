package ru.dimangan.easy_ui.dto.internal;

import com.jcraft.jsch.ChannelSftp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.io.OutputStream;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SFTP {
    private ChannelSftp sftp;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String home;

    public SFTP(ChannelSftp sftp, InputStream inputStream, OutputStream outputStream) {
        this.sftp = sftp;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public void close() {
        try {
            if(sftp.isConnected()){
                sftp.disconnect();
            }
            if(sftp.getSession().isConnected()){
                sftp.getSession().disconnect();
            }
        }
        catch (Exception e) {/**/}
    }
}
