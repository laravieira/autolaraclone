package me.laravieira.autolaraclone.installer;

import com.therandomlabs.curseapi.CurseAPI;
import com.therandomlabs.curseapi.CurseException;
import com.therandomlabs.curseapi.file.CurseFile;
import com.therandomlabs.curseapi.file.CurseFileFilter;
import com.therandomlabs.curseapi.file.CurseFiles;
import me.laravieira.autolaraclone.gui.Installing;
import me.laravieira.autolaraclone.resource.*;
import org.apache.commons.lang3.NotImplementedException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Downloader {
    public static final int CURSE_PROVIDER   = 3;
    public static final int FABRIC_PROVIDER  = 4;
    public static final int FORGE_PROVIDER   = 5;
    public static final int SILDURS_PROVIDER = 6;
    public static final int GITHUB_PROVIDER  = 7;
    public static final int FILE_PROVIDER    = 8;

    private final Installing panel;
    private final String version;
    private final Loader loader;
    private String folder;

    public Downloader(Installing panel, String version, Loader loader, Path tempFolder) {
        this.panel = panel;
        this.version = version;
        this.loader = loader;
        this.folder = tempFolder.toString();
    }

    public void download(Resource resource) {
        if (resource.getChecked()) {
            panel.log("Downloading "+resource.getName());
            if(resource instanceof Loader)
                this.folder = folder+File.separator;
            if(resource instanceof Mod)
                this.folder = folder+File.separator+ "mods" +File.separator;
            if(resource instanceof Texture)
                this.folder = folder+File.separator+"resourcepacks"+File.separator;
            if(resource instanceof Shader)
                this.folder = folder+File.separator+ "shaderpacks" +File.separator;
            if(resource instanceof Setting)
                this.folder = folder+File.separator+"config"+File.separator;
            new File(folder).mkdirs();

            try {
                switch (resource.getProvider()) {
                    case Downloader.FABRIC_PROVIDER -> downloadFromFabric(resource);
                    case Downloader.FORGE_PROVIDER -> downloadFromForge(resource);
                    case Downloader.CURSE_PROVIDER -> downloadFromCurse(resource);
                    case Downloader.GITHUB_PROVIDER -> downloadFromGitHub(resource);
                    case Downloader.SILDURS_PROVIDER -> downloadFromSildurs(resource);
                    case Downloader.FILE_PROVIDER -> downloadFromResources(resource);
                    default -> throw new ProviderNotFoundException("This provider is not supported.");
                }
                panel.log(resource.getName() + " downloaded.");
            }catch (Exception e) {
                panel.log("Unable to download "+resource.getName()+": "+e.getMessage());
            }
        }else panel.log("Skipping download of "+resource.getName());
        panel.addProgress();
    }

    private void downloadFromSildurs(Resource resource) {
        throw new NotImplementedException("This provider is not supported.");
    }

    private void downloadFromResources(Resource resource) throws IOException {
        String path = resource.getFile().getPath();
        InputStream stream = getClass().getClassLoader().getResource(path).openStream();

        path = folder+resource.getFile().getName();
        ReadableByteChannel rbc = Channels.newChannel(stream);
        FileOutputStream fos = new FileOutputStream(path);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        fos.close();
        rbc.close();
        resource.setFile(new File(path));
    }

    private void downloadFromGitHub(Resource resource) throws IOException {
        GitHub gh = GitHub.connectAnonymously();
        GHRepository repo = gh.getRepositoryById(resource.getId());
        List<GHAsset> assets = repo.getLatestRelease().listAssets().toList();
        String file = assets.stream()
                .filter(e -> e.getBrowserDownloadUrl().endsWith(".jar"))
                .collect(Collectors.toList())
                .get(0)
                .getBrowserDownloadUrl();
        resource.setFile(downloadFile(file));
    }

    private void downloadFromFabric(Resource resource) throws IOException {
        String latest = listLinks("https://maven.fabricmc.net/net/fabricmc/fabric-installer/", "/fabric-installer", "", "/").get(0);
        String installer = listLinks(latest, "", "", ".jar").get(0);
        resource.setFile(downloadFile(installer));
    }

    private void downloadFromForge(Resource resource) throws IOException {
        String url = "https://files.minecraftforge.net/net/minecraftforge/forge/index_"+this.version+".html";
        List<String> links = listLinks(url, "", "=https", ".jar");
        resource.setFile(downloadFile(links.get(0)));
    }

    private File downloadFile(String file) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(new URL(file).openStream());
        String path = folder+(new File(file)).getName().replaceAll("\\+", "-");
        FileOutputStream fos = new FileOutputStream(path);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.close();
        rbc.close();
        System.out.println("Path: "+path);
        System.out.println("File: "+(new File(path).getPath()));
        return new File(path);
    }

    private List<String> listLinks(String url, String contains, String notContains, String endWith) throws IOException {
        List<String> links = new ArrayList<>();
        Document doc = Jsoup.connect(url).get();
        for (Element element : doc.select("a"))
            links.add(element.absUrl("href"));
        links = links.stream()
                .filter(e
                        -> (contains.isEmpty()    ||  e.contains(contains))
                        && (notContains.isEmpty() || !e.contains(notContains))
                        && (endWith.isEmpty()     ||  e.endsWith(endWith))
                )
                .sorted()
                .collect(Collectors.toList());
        Collections.reverse(links);
        return links;
    }

    private void downloadFromCurse(Resource resource) throws CurseException, IOException {
        final Optional<CurseFiles<CurseFile>> optionalFiles = CurseAPI.files((int) resource.getId());
        Path path = Path.of(folder);

        if(optionalFiles.isPresent()) {
            final CurseFiles<CurseFile> files = optionalFiles.get();
            files.filter(new CurseFileFilter().gameVersionStrings(this.version));
            if (resource instanceof Mod)
                files.filter(p -> {
                    for(Loader loader : ((Mod) resource).getLoaders()) {
                        if(loader.getIdentifier().equals(this.loader.getIdentifier()))
                            continue;
                        if(p.displayName().toLowerCase().contains(loader.getIdentifier().toLowerCase()))
                            return false;
                    }
                    return true;
                });
            CurseFile file = files.first();
            path = file.downloadToDirectory(path);
        }
        resource.setFile(path.toFile());
    }
}
