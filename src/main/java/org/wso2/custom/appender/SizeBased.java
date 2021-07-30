package org.wso2.custom.appender;


import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.utils.logging.LoggingUtils;
import org.wso2.carbon.utils.logging.TenantAwareLoggingEvent;

import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class SizeBased  extends FileAppender {
    protected long maxFileSize = 10485760L;
    protected int maxBackupIndex = 1;
    private long nextRollover = 0L;
    private String maskingPatternFile;
    private List<Pattern> maskingPatterns;

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
            File parentDir = new File(this.fileName).getParentFile();

            FilenameFilter textFilter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    if(name.contains("wso2carbon.log")){
                        return true;
                    }else {
                        return false;
                    }
                }
            };
            File[] files = parentDir.listFiles(textFilter);
            if (checkFileName(files,file)) {
                file = getExactFileName(files,file);
                renameSucceeded = file.delete();
            }

            File target;
            for(int i = this.maxBackupIndex - 1; i >= 1 && renameSucceeded; --i) {
                file = new File(this.fileName + "." + i);

                if (checkFileName(files,file)) {
                    file = getExactFileName(files,file);
                    //Sample date looks like this wso2carbon.log.79.21082018-11-36
                    int lastIndexOf = file.getName().lastIndexOf(".");
                    String originalBackupDate = file.getName().substring(lastIndexOf+1);

                    target = new File(this.fileName + '.' + (i + 1) + "."+ originalBackupDate);
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

    //checking the log file in the directory by matching the index of the file
    private boolean checkFileName(File [] files, File newFile){
        for (File file : files) {
            if (file.getName().contains(newFile.getName() + ".")) {
                return true;
            }
            continue;
        }
        return false;
    }

    //get the log file name from the directory by matching the index of the file
    private File getExactFileName(File [] files, File newFile){
        for (File file : files) {
            if (file.getName().contains(newFile.getName() + ".")) {
                return file;
            }
            continue;
        }
        return null;
    }

    protected void setQWForFiles(Writer writer) {
        this.qw = new CountingQuietWriter(writer, this.errorHandler);
    }

    protected void subAppend(LoggingEvent event) {

        int tenantId = AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return CarbonContext.getThreadLocalCarbonContext().getTenantId();
            }
        });

        String serviceName = CarbonContext.getThreadLocalCarbonContext().getApplicationName();

        // acquire the tenant aware logging event from the logging event
        TenantAwareLoggingEvent tenantAwareLoggingEvent = LoggingUtils
                .getTenantAwareLogEvent(this.maskingPatterns, event, tenantId, serviceName);
        super.subAppend(tenantAwareLoggingEvent);
        //super.subAppend(event);
        if (this.fileName != null && this.qw != null) {
            long size = ((CountingQuietWriter)this.qw).getCount();
            if (size >= this.maxFileSize && size >= this.nextRollover) {
                this.rollOver();
            }
        }

    }

    public String getMaskingPatternFile() {

        return this.maskingPatternFile;
    }

    /**
     * Set the maskingPatternFile parameter.
     * In this method, masking patterns will be loaded from the provided file.
     *
     * @param maskingPatternFile : The absolute path of the masking pattern file.
     */
    public void setMaskingPatternFile(String maskingPatternFile) {

        this.maskingPatterns = LoggingUtils.loadMaskingPatterns(maskingPatternFile);
    }
}
