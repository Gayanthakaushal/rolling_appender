package org.wso2.custom.appender;


import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SizeBased  extends FileAppender {
    protected long maxFileSize = 10485760L;
    protected int maxBackupIndex = 1;
    private long nextRollover = 0L;

    public SizeBased() {
    }

    public SizeBased(Layout layout, String filename, boolean append) throws IOException {
        super(layout, filename, append);
    }

    public SizeBased(Layout layout, String filename) throws IOException {
        super(layout, filename);
    }

    public int getMaxBackupIndex() {
        return this.maxBackupIndex;
    }

    public long getMaximumFileSize() {
        return this.maxFileSize;
    }

    public void rollOver() {
        if (this.qw != null) {
            long size = ((CountingQuietWriter)this.qw).getCount();
            LogLog.debug("rolling over count=" + size);
            this.nextRollover = size + this.maxFileSize;
        }

        Date date = new Date();
        Timestamp ts=new Timestamp(date.getTime());
        SimpleDateFormat formatter = new SimpleDateFormat("ddMMYYYY-HH-mm");
        String newDate = formatter.format(ts);

        LogLog.debug("maxBackupIndex=" + this.maxBackupIndex);
        boolean renameSucceeded = true;
        if (this.maxBackupIndex > 0) {
            File file = new File(this.fileName + '.' + this.maxBackupIndex);
            if (file.exists()) {
                renameSucceeded = file.delete();
            }

            File target;
            for(int i = this.maxBackupIndex - 1; i >= 1 && renameSucceeded; --i) {
                file = new File(this.fileName + "." + i + "."+ newDate);
                if (file.exists()) {
                    target = new File(this.fileName + '.' + (i + 1) + "."+ newDate);
                    LogLog.debug("Renaming file " + file + " to " + target);
                    renameSucceeded = file.renameTo(target);
                }
            }

            if (renameSucceeded) {
                target = new File(this.fileName + "." + 1 + "." + newDate);
                this.closeFile();
                file = new File(this.fileName);
                LogLog.debug("Renaming file " + file + " to " + target);
                renameSucceeded = file.renameTo(target);
                if (!renameSucceeded) {
                    try {
                        this.setFile(this.fileName, true, this.bufferedIO, this.bufferSize);
                    } catch (IOException var6) {
                        if (var6 instanceof InterruptedIOException) {
                            Thread.currentThread().interrupt();
                        }

                        LogLog.error("setFile(" + this.fileName + ", true) call failed.", var6);
                    }
                }
            }
        }

        if (renameSucceeded) {
            try {
                this.setFile(this.fileName, false, this.bufferedIO, this.bufferSize);
                this.nextRollover = 0L;
            } catch (IOException var5) {
                if (var5 instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                }

                LogLog.error("setFile(" + this.fileName + ", false) call failed.", var5);
            }
        }

    }

    public synchronized void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize) throws IOException {
        super.setFile(fileName, append, this.bufferedIO, this.bufferSize);
        if (append) {
            File f = new File(fileName);
            ((CountingQuietWriter)this.qw).setCount(f.length());
        }

    }

    public void setMaxBackupIndex(int maxBackups) {
        this.maxBackupIndex = maxBackups;
    }

    public void setMaximumFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void setMaxFileSize(String value) {
        this.maxFileSize = OptionConverter.toFileSize(value, this.maxFileSize + 1L);
    }

    protected void setQWForFiles(Writer writer) {
        this.qw = new CountingQuietWriter(writer, this.errorHandler);
    }

    protected void subAppend(LoggingEvent event) {
        super.subAppend(event);
        if (this.fileName != null && this.qw != null) {
            long size = ((CountingQuietWriter)this.qw).getCount();
            if (size >= this.maxFileSize && size >= this.nextRollover) {
                this.rollOver();
            }
        }

    }
}
