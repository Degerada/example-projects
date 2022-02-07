package com.novamaday.d4j.maven.simplebot;

import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class GlobalCommandRegistrar {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final RestClient restClient;

    public GlobalCommandRegistrar(RestClient restClient) {
        this.restClient = restClient;
    }

    //Since this will only run once on startup, blocking is okay.
    protected void registerCommands() throws IOException {
        //Create an ObjectMapper that supports Discord4J classes
        final JacksonResources d4jMapper = JacksonResources.create();

        // Convenience variables for the sake of easier to read code below
        final ApplicationService applicationService = restClient.getApplicationService();
        final long applicationId = restClient.getApplicationId().block();

        //Get our commands json from resources as command data
        List<ApplicationCommandRequest> commands = new ArrayList<>();
        for (String json : getCommandsJson()) {
            ApplicationCommandRequest request = d4jMapper.getObjectMapper()
                .readValue(json, ApplicationCommandRequest.class);

            commands.add(request); //Add to our array list
        }

        /* Bulk overwrite commands. This is now idempotent, so it is safe to use this even when only 1 command
        is changed/added/removed
        */
        applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, commands)
            .doOnNext(ignore -> LOGGER.debug("Successfully registered Global Commands"))
            .doOnError(e -> LOGGER.error("Failed to register global commands", e))
            .subscribe();
    }

    /* The two below methods are boilerplate that can be completely removed when using Spring Boot */

    private List<String> getCommandsJson() throws IOException {
        //The name of the folder the commands json is in, inside our resources folder
        final String commandsFolderName = "commands/";

        List<String> resourcePaths = new ArrayList<>();

        //Read the contents of the JarFile
        try (JarFile jarFile = new JarFile(new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String entryName = entry.getName();
                if(entry.isDirectory()){ // We don't try to read file-content of a directory
                    continue;
                }

                if (entryName.startsWith(commandsFolderName)) {
                    LOGGER.debug(entryName);
                    resourcePaths.add(entryName);
                }
            }

        } catch (URISyntaxException exception) {
            LOGGER.error("Could not read contents of JarFile!");
        }

        LOGGER.debug(resourcePaths.toString());

        //Read the file contents of all found files and return them
        List<String> fileContents = new ArrayList<>();
        for (String resourcePath : resourcePaths) {
            String resourceFileAsString = getResourceFileAsString(resourcePath);
            LOGGER.debug(resourceFileAsString);
            fileContents.add(resourceFileAsString);
        }
        return fileContents;
    }

    /**
     * Gets a specific resource file as String
     * @param fileName The file path omitting "resources/"
     * @return The contents of the file as a String, otherwise throws an exception
     */
    private static String getResourceFileAsString(String fileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try (InputStream resourceAsStream = classLoader.getResourceAsStream(fileName)) {
            if (resourceAsStream == null) return null;
            try (InputStreamReader inputStreamReader = new InputStreamReader(resourceAsStream);
                 BufferedReader reader = new BufferedReader(inputStreamReader)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }
}
