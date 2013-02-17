package com.vonhof.webi.maven;

import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenWorkspace implements WorkspaceReader {
    private final WorkspaceRepository workspaceRepository;
    private Map<String,Map<String,Map<String,Artifact>>> artifacts = new HashMap<String, Map<String, Map<String, Artifact>>>();

    public MavenWorkspace(String id) {
        workspaceRepository = new WorkspaceRepository(id);
    }

    @Override
    public WorkspaceRepository getRepository() {
        return workspaceRepository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        final Map<String, Artifact> versions = getVersions(artifact.getGroupId(), artifact.getArtifactId());

        final Artifact version = versions.get(artifact.getVersion());
        if (version == null) {
            return null;
        }

        return version.getFile();
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        return new ArrayList<String>(getVersions(artifact.getGroupId(),artifact.getArtifactId()).keySet());
    }

    public void addArtifact(Artifact artifact) {
        final Map<String, Artifact> versions = getVersions(artifact.getGroupId(), artifact.getArtifactId());
        versions.put(artifact.getVersion(),artifact);
    }

    private Map<String,Artifact> getVersions(String groupId,String artifactId) {
        if (!artifacts.containsKey(groupId)) {
            artifacts.put(groupId,new HashMap<String, Map<String, Artifact>>());
        }
        if (!artifacts.get(groupId).containsKey(artifactId)) {
            artifacts.get(groupId).put(artifactId,new HashMap<String, Artifact>());
        }
        return artifacts.get(groupId).get(artifactId);
    }
}
