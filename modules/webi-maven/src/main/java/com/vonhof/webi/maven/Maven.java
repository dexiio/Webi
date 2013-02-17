package com.vonhof.webi.maven;


import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.impl.ArtifactDescriptorReader;
import org.sonatype.aether.impl.VersionRangeResolver;
import org.sonatype.aether.impl.VersionResolver;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.repository.*;
import org.sonatype.aether.resolution.*;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Maven {
    private final DefaultServiceLocator locator = new DefaultServiceLocator();

    private final RepositorySystem repositorySystem;
    private final LocalRepository localRepo;
    private final String localRepositoryPath;

    private final LocalRepositoryManager localRepoManager;
    private final List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

    private final MavenWorkspace workspace = new MavenWorkspace("workspace");

    private MavenRepositorySystemSession session;

    public Maven() {
        String userHome = System.getProperty("user.home");

        localRepositoryPath = userHome+"/.m2/repository/";

        locator.addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);
        locator.addService(VersionResolver.class, DefaultVersionResolver.class);
        locator.addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class);
        locator.addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class);
        locator.addService(RepositoryConnectorFactory.class, AsyncRepositoryConnectorFactory.class);

        repositorySystem = locator.getService(RepositorySystem.class);

        localRepo = new LocalRepository(localRepositoryPath);
        localRepoManager = repositorySystem.newLocalRepositoryManager(localRepo);

        addRepository("central","http://repo.maven.apache.org/maven2/",true,false);
    }


    /**
     * Resolve artifact.
     *
     * @see Maven::resolveArtifact(String artifactId);
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     * @throws ArtifactResolutionException
     * @throws DependencyCollectionException
     */
    public Artifact resolveArtifact(String groupId,String artifactId,String version) throws ArtifactResolutionException, DependencyCollectionException {
        return resolveArtifact(String.format("%s:%s:%s",groupId,artifactId,version));
    }

    /**
     * Resolve artifact.
     *
     * @see Maven::resolveArtifact(String artifactId);
     *
     * @param groupId
     * @param artifactId
     * @param type
     * @param version
     * @return
     * @throws ArtifactResolutionException
     * @throws DependencyCollectionException
     */
    public Artifact resolveArtifact(String groupId,String artifactId,String type,String version) throws ArtifactResolutionException, DependencyCollectionException {
        return resolveArtifact(String.format("%s:%s:%s:%s", groupId, artifactId, type, version));
    }

    /**
     * Resolve dependencies for artifact - and returns a list of all dependencies as resolved artifacts.
     *
     * @param artifactId
     * @return
     * @throws DependencyCollectionException
     * @throws DependencyResolutionException
     */
    public List<Artifact> resolveDependencies(String artifactId) throws DependencyCollectionException, DependencyResolutionException, ArtifactResolutionException {
        Artifact artifact = resolveArtifact(artifactId);
        Dependency dependency = new Dependency( artifact, "compile" );

        //Collect dependencies
        CollectRequest collectRequest = new CollectRequest();

        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(getRemoteRepositories());
        DependencyNode node = repositorySystem.collectDependencies( session(), collectRequest ).getRoot();


        //Resolve dependencies
        DependencyRequest dependencyRequest = new DependencyRequest( node, null );
        //dependencyRequest.setCollectRequest(collectRequest);
        repositorySystem.resolveDependencies( session(), dependencyRequest);

        //Order result
        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept( nlg );
        return nlg.getArtifacts(true);
    }

    /**
     * Resolve artifact by id. Downloads artifact from remote repository if needed.
     * Returned artifact instance has contains file pointer to locale file.
     *
     * @param artifactId
     * @return
     * @throws ArtifactResolutionException
     * @throws DependencyCollectionException
     */
    public Artifact resolveArtifact(String artifactId) throws ArtifactResolutionException, DependencyCollectionException {

        Artifact artifact = new DefaultArtifact(artifactId);
        Dependency dependency = new Dependency( artifact, "compile" );

        //Resolve artifact
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(getRemoteRepositories());
        final ArtifactResult result = repositorySystem.resolveArtifact(session(), artifactRequest);

        return result.getArtifact();
    }

    public void addRepository(String id,String url) {
        addRepository(id,url,true,true);
    }

    public void addRepository(String id,String url,boolean release) {
        addRepository(id,url,release,true);
    }

    /**
     * Add remote repository. Maven central is added automatically.
     * @param id
     * @param url
     */
    public void addRepository(String id,String url,boolean release,boolean snapshots) {
        final RemoteRepository repo = new RemoteRepository(id, "default", url);

        repo.setPolicy(false,new RepositoryPolicy().setEnabled(release));
        repo.setPolicy(true,new RepositoryPolicy().setEnabled(snapshots));

        remoteRepositories.add(repo);
    }

    /**
     * Add workspace artifact to this maven instance. Will overwrite everything else for that particular artifact.
     * @param artifact
     */
    public void addArtifact(Artifact artifact) {
        workspace.addArtifact(artifact);
    }

    protected List<RemoteRepository> getRemoteRepositories() {
        return remoteRepositories;
    }

    protected RepositorySystemSession session() {
        if (session == null) {
            session = new MavenRepositorySystemSession();
            session.setLocalRepositoryManager(localRepoManager);
            session.setWorkspaceReader(workspace);
        }

        return session;
    }
}
