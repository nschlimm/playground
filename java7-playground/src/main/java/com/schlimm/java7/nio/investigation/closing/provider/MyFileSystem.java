package com.schlimm.java7.nio.investigation.closing.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

public class MyFileSystem extends FileSystem {

	private FileSystem wrappedFileSystem = FileSystems.getDefault();
	
	@Override
	public FileSystemProvider provider() {
		for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
			if (provider.getScheme().equals("safe")) {
				return provider;
			}
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		wrappedFileSystem.close();
	}

	@Override
	public boolean isOpen() {
		return wrappedFileSystem.isOpen();
	}

	@Override
	public boolean isReadOnly() {
		return wrappedFileSystem.isReadOnly();
	}

	@Override
	public String getSeparator() {
		return wrappedFileSystem.getSeparator();
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return wrappedFileSystem.getRootDirectories();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return wrappedFileSystem.getFileStores();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return wrappedFileSystem.supportedFileAttributeViews();
	}

	@Override
	public Path getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment: more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0)
                        sb.append('/');
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
		try {
			return provider().getPath(new URI(path));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return wrappedFileSystem.getPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return wrappedFileSystem.getUserPrincipalLookupService();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return wrappedFileSystem.newWatchService();
	}

}
