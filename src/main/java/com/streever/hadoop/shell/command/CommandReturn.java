/*
 *  Hadoop CLI
 *
 *  (c) 2016-2019 David W. Streever. All rights reserved.
 *
 * This code is provided to you pursuant to your written agreement with David W. Streever, which may be the terms of the
 * Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 * to distribute this code.  If you do not have a written agreement with David W. Streever or with an authorized and
 * properly licensed third party, you do not have any rights to this code.
 *
 * If this code is provided to you under the terms of the AGPLv3:
 * (A) David W. Streever PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 * (B) David W. Streever DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *   LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 * (C) David W. Streever IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *    FROM OR RELATED TO THE CODE; AND
 *  (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, David W. Streever IS NOT LIABLE FOR ANY
 *    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *   DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 *
 */

package com.streever.hadoop.shell.command;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandReturn {
    public static int GOOD = 0;
    public static int BAD = -1;

    private int code = 0;
    private String[] commandArgs = null;
    private String path = null;
    private List<List<Object>> records = new ArrayList<List<Object>>();

    private ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
    private ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
    private PrintStream out = new PrintStream(baosOut);
    private PrintStream err = new PrintStream(baosErr);

    public List<List<Object>> getRecords() {
        if (records.size() == 0 && baosOut.size() > 0) {
            BufferedReader bufferedReader;
            String line = null;
            bufferedReader = new BufferedReader(new StringReader(new String(baosOut.toByteArray())));
            // When the record size is 0, this means the records from the
            // command call are in the baosOut, read from the 'shell' of a
            // native Hadoop Shell Command.  We'll convert over to
            // records for better processing.
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    if (!line.startsWith("Found")) {
                        String[] parts = line.trim().split("\\s{1,}");
                        List<Object> record = Arrays.asList(parts);
                        records.add(record);
                    }
                }
            } catch (IOException ioe) {
                //
            }
        }
        return records;
    }

    public boolean addRecord(List<Object> record) {
        boolean rtn = records.add(record);
        return rtn;
    }

    public String[] getCommandArgs() {
        return commandArgs;
    }

    public String getCommand() {
        return StringUtils.join(commandArgs, " ");
    }

    public void setCommandArgs(String[] commandArgs) {
        this.commandArgs = commandArgs;
    }

    public void setCommandArgs(List<String> commandArgs) {
        String[] args = new String[commandArgs.size()];
        commandArgs.toArray(args);
        this.commandArgs = args;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isError() {
        if (code != 0)
            return true;
        else
            return false;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public PrintStream getOut() {
        return out;
    }

    public PrintStream getErr() {
        return err;
    }

    public CommandReturn(int code) {
        this.code = code;
    }

    public String getReturn() {
        String outString = new String(this.baosOut.toByteArray());
        if ((outString != null && outString.length() > 0) || records.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(outString);
            if (records.size() > 0) {
//            for (List<String> record : records) {
                for (int i = 0; i < records.size(); i++) {
                    List<Object> record = records.get(i);
                    for (int j = 0; j < record.size(); j++) {
                        sb.append(record.get(j));
                        if (j < record.size() - 1) {
                            sb.append("\t");
                        }
                    }
                    if (i < records.size() - 1) {
                        sb.append("\n");
                    }
                }
            }
            return sb.toString();
        } else {
            return null;
        }
    }

    public String getError() {
        String rtn = new String(this.baosErr.toByteArray());
        return rtn;
    }

}
