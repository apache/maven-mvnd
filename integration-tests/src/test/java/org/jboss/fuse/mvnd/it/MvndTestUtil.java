package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MvndTestUtil {

    private MvndTestUtil() {
    }

    public static String plugin(Properties props, String artifactId) {
        return artifactId + ":" + props.getProperty(artifactId + ".version");
    }

    public static Properties properties(Path pomXmlPath) {
        try (Reader runtimeReader = Files.newBufferedReader(pomXmlPath, StandardCharsets.UTF_8)) {
            final MavenXpp3Reader rxppReader = new MavenXpp3Reader();
            return rxppReader.read(runtimeReader).getProperties();
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Could not read or parse " + pomXmlPath);
        }
    }

}
