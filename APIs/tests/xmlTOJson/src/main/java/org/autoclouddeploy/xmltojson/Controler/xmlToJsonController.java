package org.autoclouddeploy.xmltojson.Controler;

import org.autoclouddeploy.xmltojson.services.XmlToJsonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;

@RestController
public class xmlToJsonController {
    @PostMapping("/convert")
    public String convertXmlToJson(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return "{}";
        }

        File tempXml = Files.createTempFile("upload-", ".xml").toFile();
        File tempJson = Files.createTempFile("result-", ".json").toFile();
        try {
            file.transferTo(tempXml);
            XmlToJsonService.convertXmlFileToJsonFile(tempXml, tempJson);
            return Files.readString(tempJson.toPath());
        } finally {
            tempXml.delete();
            tempJson.delete();
        }
    }
}
