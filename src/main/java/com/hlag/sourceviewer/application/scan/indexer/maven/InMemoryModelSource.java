package com.hlag.sourceviewer.application.scan.indexer.maven;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.maven.model.building.ModelSource2;

@SuppressWarnings("java:S6218" /* hashcode and equals */)
record InMemoryModelSource(byte[] content) implements ModelSource2 {
  public InMemoryModelSource(String content) {
    this(content.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public String getLocation() {
    return "memory";
  }

  @Override
  public ModelSource2 getRelatedSource(String relPath) {
    return null; // cannot resolve other artifacts from memory
  }

  @Override
  public URI getLocationURI() {
    return URI.create("memory:///here");
  }
}
