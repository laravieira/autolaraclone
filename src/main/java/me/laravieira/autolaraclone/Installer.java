package me.laravieira.autolaraclone;

import me.laravieira.autolaraclone.gui.Installing;
import me.laravieira.autolaraclone.installer.Downloader;
import me.laravieira.autolaraclone.installer.Profile;
import me.laravieira.autolaraclone.resource.Loader;
import me.laravieira.autolaraclone.resource.Mod;
import me.laravieira.autolaraclone.resource.Shader;
import me.laravieira.autolaraclone.resource.Texture;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.json.JSONObject;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Installer extends Thread {
    private final String baseDir;
    private final String gameDir;
    private final String version;
    private final Installing panel;
    private final Loader loader;
    private final Map<String, Boolean> preferences;

    public Installer(Installing panel, String version, Loader loader, Map<String, Boolean> preferences) {
        this.panel = panel;
        this.version = version;
        this.loader = loader;
        this.preferences = preferences;

        this.baseDir = System.getenv("APPDATA") + File.separator + ".minecraft.shignima";
        if (preferences.get("create-ver-dir"))
            this.gameDir = this.baseDir + File.separator + this.version;
        else this.gameDir = this.baseDir;
        new File(this.gameDir).mkdirs();
    }

    @Override
    public void run() {
        panel.log("Downloading updated resources...");

        Downloader downloader = new Downloader(this.panel, this.version, this.loader);

        // Download and install loader
        downloader.download(loader);
        runJarAndWait(loader.getFile().getPath());

        // Download and copy all other resources
        for (Mod mod : Populate.mods) {
            downloader.download(mod);
            copy(mod.getFile(), "mods");
        }for(Texture texture :Populate.textures) {
            downloader.download(texture);
            copy(texture.getFile(), "resourcepacks");
        }for (Shader shader : Populate.shaders) {
            downloader.download(shader);
            copy(shader.getFile(), "shaderpacks");
        }

        // Add new Profile
        String name = loader.getFile().getName();
        addProfile(findVersionId(name));

        panel.log("Done! You can close this.");
        panel.addProgress(true);
    }

    private void copy(File file, String path) {
        try {
            if(file.exists()) {
                path = gameDir+File.separator+path+File.separator;
                new File(path).mkdirs();

                File fnew = new File(path+file.getName());
                if(fnew.exists()) {
                    new File(path+"old").mkdirs();
                    File backup = new File(path+"old"+File.separator+fnew.getName());
                    Files.move(fnew.toPath(), backup.toPath(), REPLACE_EXISTING);
                }

                Files.move(file.toPath(), fnew.toPath(), REPLACE_EXISTING);
                panel.log(file.getName()+" copied.");
            }
            panel.addProgress();
        } catch (IOException e) {
            panel.log("Unable to copy: "+e.getMessage());
            e.printStackTrace();
        }
    }

    private void addProfile(String versionId) {
        if(!preferences.get("create-profile"))
            return;
        try {
            Profile lara = new Profile("L4R4 "+this.version, this.gameDir, versionId);
            String path = baseDir+File.separator+"launcher_profiles.json";
            JSONObject profiles = new JSONObject(Files.readString(new File(path).toPath()));
            profiles.getJSONObject("profiles").put("L4R4", lara.toJSONObject());
            FileWriter writer = new FileWriter(path);
            writer.write(profiles.toString());
            writer.close();
            panel.log("Profile created.");
        } catch (IOException e) {
            panel.log("Unable to create profile: "+e.getMessage());
        }
    }

    private String findVersionId(String name) {
        List<String>  ids = Arrays.stream(new File(baseDir+File.separator+"versions").list()).toList();

        // Filter to only MC version of loaders
        // Make the first match always be the lasted loader version
        ids = ids.stream()
                .filter(e -> e.contains("-") && e.contains(this.version))
                .sorted()
                .collect(Collectors.toList());
        Collections.reverse(ids);

        for(String id : ids) {
            String[] subsId = id
                    // Remove MC version to avoid wrong matches
                    .replace(this.version+"-", "")
                    .replace("-"+this.version, "")
                    .split("-");
            for(String subId : subsId)
                if (name.toLowerCase().contains(subId.toLowerCase()))
                    return id;
        }
        return this.version;
    }

    public void runJarAndWait(String file) {
        try {
            panel.log("Starting Loader installer...");
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("java -jar "+file);
            process.getInputStream().transferTo(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    char c = (char)b;
                    panel.getLog().append(""+c);
                }
            });
            process.waitFor();
            panel.log("Loader should be installed.");
        } catch (IOException | InterruptedException e) {
            panel.log("Loader not installed: "+e.getMessage());
        }
    }
}