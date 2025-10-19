package org.autoclouddeploy.xmltojson.services;


import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class XmlToJsonService {

//    this methode will convert xml file to json
public static void convertXmlFileToJsonFile(File xmlFile, File jsonFile) throws IOException, InterruptedException {
        if (xmlFile == null || !xmlFile.exists()) {
            throw new IllegalArgumentException("xmlFile does not exist");
        }

        // prefer python launcher on Windows; adjust if your environment uses 'python3' or full path
        String pythonExe = "C:/Users/rajya/AppData/Local/Programs/Python/Python312/python.exe";

        // use absolute path to the script to avoid working-directory issues
        String scriptPath = new File("tools/xml_parser.py").getAbsolutePath();

        List<String> command = new ArrayList<>();
        command.add(pythonExe);
        command.add(scriptPath);
        command.add(xmlFile.getAbsolutePath());

        // IMPORTANT: pass the List<String> to ProcessBuilder (not String.valueOf(command))
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Python parser exited with code " + exitCode + ". Output:\n" + output.toString());
        }

        Files.writeString(jsonFile.toPath(), output.toString(), StandardCharsets.UTF_8);
    }
}
