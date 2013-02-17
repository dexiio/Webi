package com.vonhof.webi.maven;

import org.junit.Test;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class MavenTest {

    @Test
    public void testResolveArtifact() throws Exception {
        Maven mvn = new Maven();
        final Artifact artifact = mvn.resolveArtifact("com.hazelcast:hazelcast:2.5");

        assertTrue("Got a jar file that exists",artifact.getFile().exists());
    }


    @Test
    public void testResolveDependencies() throws Exception {
        Maven mvn = new Maven();

        final List<Artifact> artifacts = mvn.resolveDependencies("org.springframework:spring-core:3.2.1.RELEASE");

        assertTrue("Found more than 1 dependency (result includes itself)",artifacts.size() > 1);
    }

    @Test
    public void testAddArtifact() throws Exception {
        Maven mvn = new Maven();

        boolean gotException = false;
        try {
            mvn.resolveArtifact("com.example:nothing:6.6.6");

        } catch(ArtifactResolutionException ex) {
            gotException = true;
        }
        assertTrue("Artifact could not be resolved",gotException);

        Artifact artifact = new DefaultArtifact("com.example:nothing:6.6.6")
                                .setFile(new File("com/example/nothing-6.6.6.jar"));

        mvn.addArtifact(artifact);

        Artifact resolvedArtifact = mvn.resolveArtifact("com.example:nothing:6.6.6");

        assertNotNull("Artifact can now be resolved",resolvedArtifact);
    }
}
