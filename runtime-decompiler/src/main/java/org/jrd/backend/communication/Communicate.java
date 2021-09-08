package org.jrd.backend.communication;


import org.jrd.backend.core.OutputController;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * This class opens a socket and contain methods for read and write to socket
 * IS/OS.
 */
public class Communicate {

    private Socket commSocket;
    private BufferedReader commInput;
    private BufferedWriter commOutput;
    //private static final Logger logger = LoggingUtils.getLogger(Communicate.class);

    /**
     * Constructor creates a socket on given port and saves the streams into
     * class variables.
     *
     * @param host host name
     * @param port port where we open the socket
     */
    public Communicate(String host, int port) {
        try {
            this.commSocket = new Socket(host, port);
        } catch (IOException ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, ex);
        }
        InputStream is;
        try {
            is = this.commSocket.getInputStream();
        } catch (IOException e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, e);
            try {
                this.commSocket.close();
            } catch (IOException e1) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, e1);
            }
            return;
        }

        OutputStream os;
        try {
            os = this.commSocket.getOutputStream();
        } catch (IOException e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, e);
            try {
                this.commSocket.close();
            } catch (IOException e1) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, e1);
            }
            return;
        }

        this.commInput = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        this.commOutput = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
    }

    /**
     * Closes a socket.
     */
    public void close() {
        try {
            this.commSocket.close(); // also closes the in/out streams
        } catch (IOException e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, e);
        } finally {
            this.commSocket = null;
            this.commInput = null;
            this.commOutput = null;
        }
    }

    private String trimReadLine() throws IOException {
        String line = this.commInput.readLine();

        if (line == null) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, new RuntimeException("Agent returned null response."));
            return "ERROR";
        }

        return line.trim();
    }

    /**
     * Method that reads agent's response.
     * @return "ERROR" in case of fail or corresponding bytes or class names
     */
    public String readResponse(){
        String initLine;

        try {
            initLine = trimReadLine();
        } catch (IOException ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, ex);
            return "ERROR";
        }
        
        if (initLine.equals("ERROR")) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, new RuntimeException("Agent returned error."));
            return "ERROR";
       } else if (initLine.equals("BYTES")) {
            try {
                String bytes = trimReadLine();

                OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, "Agent returned bytes: "+ bytes);
                return bytes;
            } catch (IOException ex) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, ex);
            }
        } else if (initLine.equals("CLASSES")) {
            StringBuilder str = new StringBuilder();
            while (true) {
                try {
                    String s = this.commInput.readLine();
                    if (s == null) {
                        break;
                    }
                    s = s.trim();
                    if (!s.isEmpty()) {
                        str.append(s).append(";");
                    }
                } catch (IOException ex) {
                    OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, ex);
                }
            }
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG,"Agent returned class names.");
            return str.toString();
            // Agent shutdown response
        } else if(initLine.equals("CLASSINFOS")){
            StringBuilder str = new StringBuilder();
            while (true) {
                try {
                    String s = this.commInput.readLine();
                    if (s == null) {
                        break;
                    }
                    s = s.trim();
                    if (!s.isEmpty()) {
                        str.append(s).append(";");
                    }
                } catch (IOException ex) {
                    OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, ex);
                }
            }
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG,"Agent returned class names.");
            return str.toString();
        } else if (initLine.equals("GOODBYE")) {
            return "OK";
            //generic done for non-returning  commands. Eg overwrite response. Made this confirmation more  granular? done-overwrite?
        } else if (initLine.equals("DONE")) {
            return "OK";
        }
        
        OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, "Unknown header of " + initLine);
        return "ERROR";
    }

    /**
     * Sends a line with request to agent.
     * @param line "CLASSES" or "BYTES className"
     * @throws IOException if the write operation fails
     */
    public void println(String line) throws IOException {
        this.commOutput.write(line);
        this.commOutput.newLine();
        this.commOutput.flush();
    }

}
