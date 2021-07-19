package Other;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class MyParser {
    @Parameter(names = {"-p", "-port"}, description = "Server connection's port")
    private static Integer clientPort = CONSTANTS.DEFAULT_SERVER_PORT;

    @Parameter(names = {"-ip"}, description = "Server connection's ip-address")
    private static String ip = CONSTANTS.DEFAULT_SERVER_IP;

    @Parameter(names = {"-f", "-file"}, description = "Sending file")
    private static String filePath = CONSTANTS.DEFAULT_FILE_PATH;

    public MyParser(String[] args) {
        JCommander.newBuilder()
                .addObject(this)
                .build()
                .parse(args);
    }

    public static Integer getPort() {
        return clientPort;
    }

    public static String getIP() {
        return ip;
    }

    public static String getFilePath() {
        return filePath;
    }
}

